/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.extract.base.server;

import io.netty.channel.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.dolphinscheduler.extract.base.StandardRpcRequest;
import org.apache.dolphinscheduler.extract.base.StandardRpcResponse;
import org.apache.dolphinscheduler.extract.base.protocal.HeartBeatTransporter;
import org.apache.dolphinscheduler.extract.base.protocal.Transporter;
import org.apache.dolphinscheduler.extract.base.protocal.TransporterHeader;
import org.apache.dolphinscheduler.extract.base.serialize.JsonSerializer;
import org.apache.dolphinscheduler.extract.base.utils.ChannelUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * 动态RPC请求处理器（观察者模式 + 策略模式）
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>请求路由</b>：根据methodIdentifier分发到对应ServerMethodInvoker</li>
 *   <li><b>协议解析</b>：处理Transporter编解码逻辑</li>
 *   <li><b>线程隔离</b>：通过独立线程池执行业务方法</li>
 * </ol>
 *
 * <p>应用设计模式：
 * <ul>
 *   <li><b>观察者模式</b>：methodInvokerMap维护方法调用器集合</li>
 *   <li><b>策略模式</b>：不同ServerMethodInvoker实现不同调用策略</li>
 *   <li><b>工厂方法模式</b>：通过methodInvoker创建具体处理实例</li>
 * </ul>
 */
@Slf4j
@ChannelHandler.Sharable
class JdkDynamicServerHandler extends ChannelInboundHandlerAdapter {

    /**
     * 方法调用线程池（生产者-消费者模式）
     * <p>作用：隔离网络IO与业务处理线程，避免阻塞Netty事件循环
     */
    private final ExecutorService methodInvokeExecutor;

    /**
     * 方法调用器注册表（注册中心模式）
     * <p>数据结构：ConcurrentHashMap保证线程安全
     * <p>键值对：methodIdentify → ServerMethodInvoker
     */
    private final Map<String, ServerMethodInvoker> methodInvokerMap;

    /**
     * 构造方法（依赖注入模式）
     * @param methodInvokeExecutor 从NettyRemotingServer注入的线程池
     */
    JdkDynamicServerHandler(ExecutorService methodInvokeExecutor) {
        this.methodInvokeExecutor = methodInvokeExecutor;
        this.methodInvokerMap = new ConcurrentHashMap<>();
    }

    /**
     * 处理通道关闭事件（Netty生命周期管理）
     * <p>触发时机：
     * <ul>
     *   <li>客户端主动断开连接</li>
     *   <li>服务器主动关闭连接</li>
     *   <li>网络异常导致连接中断</li>
     * </ul>
     * <p>实现逻辑：级联关闭底层Channel资源
     * @param ctx ChannelHandler上下文对象
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ctx.channel().close();
    }

    /**
     * 处理网络请求（反应器模式）
     * <p>执行流程：
     * 1. 解码Transporter获取methodIdentifier
     * 2. 查找对应的ServerMethodInvoker
     * 3. 提交线程池异步执行
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        processReceived(ctx.channel(), (Transporter) msg);
    }

    /**
     * 注册方法调用器（注册中心模式）
     * @param methodInvoker 包含：
     * <ul>
     *   <li>methodIdentify：方法唯一标识符</li>
     *   <li>invoke()：实际方法调用逻辑</li>
     * </ul>
     */
    public void registerMethodInvoker(ServerMethodInvoker methodInvoker) {
        checkNotNull(methodInvoker);
        checkNotNull(methodInvoker.getMethodIdentify());

        methodInvokerMap.put(methodInvoker.getMethodIdentify(), methodInvoker);
    }

    /**
     * 处理RPC请求的核心逻辑（异步处理模式）
     *
     * <p>执行流程：
     * 1. 心跳检测 → 直接返回（空对象模式）
     * 2. 查找方法调用器 → 不存在则返回错误
     * 3. 提交线程池 → 执行实际方法调用（线程池模式）
     * 4. 序列化结果 → 返回响应（生产者-消费者模式）
     *
     * <p>关键处理逻辑：
     * 1. 参数反序列化：JSON → Java对象（JsonSerializer）
     * 2. 动态方法调用：通过methodInvoker.invoke()反射执行
     * 3. 背压控制：线程池满时拒绝请求并返回错误
     */
    private void processReceived(final Channel channel, final Transporter transporter) {
        // ... 心跳检测逻辑 ...
        final String methodIdentifier = transporter.getHeader().getMethodIdentifier();
        if (HeartBeatTransporter.METHOD_IDENTIFY.equals(methodIdentifier)) {
            if (log.isDebugEnabled()) {
                log.debug("服务器从主机接收心跳: {}", ChannelUtils.getRemoteAddress(channel));
            }
            return;
        }
        // 方法调用器查找（注册中心模式）
        ServerMethodInvoker methodInvoker = methodInvokerMap.get(methodIdentifier);
        try {
            if (methodInvoker == null) {
                log.error("找不到的ServerMethodInvoker : {}", transporter);
                StandardRpcResponse iRpcResponse =
                        StandardRpcResponse.fail("找不到的ServerMethodInvoker " + methodIdentifier);
                TransporterHeader transporterHeader =
                        TransporterHeader.of(transporter.getHeader().getOpaque(), methodIdentifier);
                Transporter response = Transporter.of(transporterHeader, iRpcResponse);
                channel.writeAndFlush(response);
                return;
            }
            // 提交线程池异步处理（资源隔离）
            methodInvokeExecutor.execute(() -> {
                StandardRpcResponse iRpcResponse;
                try {
                    StandardRpcRequest standardRpcRequest =
                            JsonSerializer.deserialize(transporter.getBody(), StandardRpcRequest.class);
                    Object[] args;
                    if (standardRpcRequest.getArgs() == null || standardRpcRequest.getArgs().length == 0) {
                        args = null;
                    } else {
                        args = new Object[standardRpcRequest.getArgs().length];
                        for (int i = 0; i < standardRpcRequest.getArgs().length; i++) {
                            args[i] = JsonSerializer.deserialize(standardRpcRequest.getArgs()[i],
                                    standardRpcRequest.getArgsTypes()[i]);
                        }
                    }
                    // 反射调用业务方法（策略模式）
                    Object result = methodInvoker.invoke(args);
                    if (result == null) {
                        iRpcResponse = StandardRpcResponse.success(null, null);
                    } else {
                        iRpcResponse = StandardRpcResponse.success(JsonSerializer.serialize(result), result.getClass());
                    }
                } catch (Throwable e) {
                    log.error("Invoke method {} failed, {}.", methodIdentifier, e.getMessage(), e);
                    iRpcResponse = StandardRpcResponse.fail(e.getMessage());
                }
                TransporterHeader transporterHeader =
                        TransporterHeader.of(transporter.getHeader().getOpaque(), methodIdentifier);
                Transporter response = Transporter.of(transporterHeader, iRpcResponse);
                channel.writeAndFlush(response);
            });
        } catch (RejectedExecutionException e) {
            log.warn("NettyRemotingServer的线程池已满，丢弃消息 {} from {}", transporter,
                    ChannelUtils.getRemoteAddress(channel));
            StandardRpcResponse iRpcResponse = StandardRpcResponse.fail("NettyRemotingServer的线程池已满");
            TransporterHeader transporterHeader =
                    TransporterHeader.of(transporter.getHeader().getOpaque(), methodIdentifier);
            Transporter response = Transporter.of(transporterHeader, iRpcResponse);
            channel.writeAndFlush(response);
        }
    }

    /**
     * 异常处理（容错模式）
     * <p>处理场景：
     * 1. 网络通信异常
     * 2. 序列化/反序列化失败
     * 3. 方法调用异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("exceptionCaught : {}", cause.getMessage(), cause);
        ctx.channel().close();
    }

    /**
     * 处理通道可写状态变化（Netty流量控制机制）
     * <p>功能说明：
     * <ul>
     *   <li>当写缓冲区超过高水位线时：暂停自动读取（防止OOM）</li>
     *   <li>当写缓冲区低于低水位线时：恢复自动读取</li>
     * </ul>
     *
     * <p>设计思想：
     * 通过动态调整autoRead配置实现背压控制，维护：
     * <ol>
     *   <li>高水位线：默认64KB（写缓冲区上限）</li>
     *   <li>低水位线：默认32KB（写缓冲区下限）</li>
     * </ol>
     *
     * @param ctx ChannelHandler上下文对象
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        Channel ch = ctx.channel();
        ChannelConfig config = ch.config();

        if (!ch.isWritable()) {
            if (log.isWarnEnabled()) {
                log.warn("{} is not writable, over high water level : {}",
                        ch, config.getWriteBufferHighWaterMark());
            }

            config.setAutoRead(false);
        } else {
            if (log.isWarnEnabled()) {
                log.warn("{} is writable, to low water : {}", ch, config.getWriteBufferLowWaterMark());
            }
            config.setAutoRead(true);
        }
    }

    /**
     * 空闲连接检测（心跳机制）
     * <p>触发条件：5000ms未收到READ事件
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.warn("Not receive heart beat from: {}, will close the channel", ctx.channel().remoteAddress());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

}
