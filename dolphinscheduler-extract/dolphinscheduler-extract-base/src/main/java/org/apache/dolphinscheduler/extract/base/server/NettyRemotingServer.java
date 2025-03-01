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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.dolphinscheduler.common.thread.ThreadUtils;
import org.apache.dolphinscheduler.extract.base.config.NettyServerConfig;
import org.apache.dolphinscheduler.extract.base.exception.RemoteException;
import org.apache.dolphinscheduler.extract.base.protocal.TransporterDecoder;
import org.apache.dolphinscheduler.extract.base.protocal.TransporterEncoder;
import org.apache.dolphinscheduler.extract.base.utils.NettyUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty通信服务端核心实现类（工厂模式+观察者模式）
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>网络层管理</b>：维护Boss/Worker线程组</li>
 *   <li><b>协议处理</b>：实现Transporter编解码协议</li>
 *   <li><b>请求分发</b>：通过JdkDynamicServerHandler路由RPC调用</li>
 * </ol>
 *
 * <p>应用设计模式：
 * <ul>
 *   <li><b>工厂模式</b>：通过NettyRemotingServerFactory创建实例</li>
 *   <li><b>观察者模式</b>：channelHandler处理客户端请求事件</li>
 *   <li><b>外观模式</b>：封装Netty启动/关闭的复杂流程</li>
 * </ul>
 */
@Slf4j
class NettyRemotingServer {

    /**
     * Netty服务端Channel对象（Reactor模式核心组件）
     * <p>生命周期：start()时创建，close()时销毁
     */
    private Channel serverBootstrapChannel;

    /**
     * 服务实例名称（服务标识）
     * <p>命名规则：模块名称 + "RpcServer"（如MasterRpcServer）
     */
    @Getter
    private final String serverName;

    /**
     * 方法调用线程池（生产者-消费者模式）
     * <p>配置策略：CPU核心数*2 +1 的固定大小线程池
     */
    @Getter
    private final ExecutorService methodInvokerExecutor;

    // Netty线程组（主从Reactor模式实现）
    private final EventLoopGroup bossGroup;// 接受连接请求
    // 处理IO操作
    private final EventLoopGroup workGroup;

    /**
     * 网络配置参数（不可变对象模式）
     * <p>包含：监听端口、线程数、缓冲区大小等
     */
    private final NettyServerConfig serverConfig;

    /**
     * 动态服务处理器（策略模式实现）
     * <p>功能：路由RPC请求到具体方法调用器
     */
    private final JdkDynamicServerHandler channelHandler;

    /**
     * 服务状态标记（CAS原子操作保障）
     * <p>状态转换：false → true（启动），true → false（关闭）
     */
    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    /**
     * 构造方法（工厂方法模式）
     *
     * @param serverConfig 网络配置参数
     *                     <p>关键初始化：
     *                     1. 创建IO线程组（Epoll/NIO自适应）
     *                     2. 初始化方法调用线程池
     *                     3. 准备协议处理器
     */
    NettyRemotingServer(final NettyServerConfig serverConfig) {
        this.serverConfig = serverConfig;
        this.serverName = serverConfig.getServerName();
        this.methodInvokerExecutor = ThreadUtils.newDaemonFixedThreadExecutor(
                serverName + "-methodInvoker-%d", Runtime.getRuntime().availableProcessors() * 2 + 1);
        this.channelHandler = new JdkDynamicServerHandler(methodInvokerExecutor);
        ThreadFactory bossThreadFactory =
                ThreadUtils.newDaemonThreadFactory(serverName + "-boss-%d");
        ThreadFactory workerThreadFactory =
                ThreadUtils.newDaemonThreadFactory(serverName + "-worker-%d");
        if (Epoll.isAvailable()) {
            this.bossGroup = new EpollEventLoopGroup(1, bossThreadFactory);
            this.workGroup = new EpollEventLoopGroup(serverConfig.getWorkerThread(), workerThreadFactory);
        } else {
            this.bossGroup = new NioEventLoopGroup(1, bossThreadFactory);
            this.workGroup = new NioEventLoopGroup(serverConfig.getWorkerThread(), workerThreadFactory);
        }
    }

    /**
     * 启动Netty服务端（生命周期控制）
     * <p>执行流程：
     * 1. 配置ServerBootstrap参数
     * 2. 绑定监听端口
     * 3. 注册异常处理逻辑
     *
     * @throws RemoteException 端口绑定失败时抛出
     */
    void start() {
        if (isStarted.compareAndSet(false, true)) {
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(this.bossGroup, this.workGroup)
                    .channel(NettyUtils.getServerSocketChannelClass())
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.SO_BACKLOG, serverConfig.getSoBacklog())
                    .childOption(ChannelOption.SO_KEEPALIVE, serverConfig.isSoKeepalive())
                    .childOption(ChannelOption.TCP_NODELAY, serverConfig.isTcpNoDelay())
                    .childOption(ChannelOption.SO_SNDBUF, serverConfig.getSendBufferSize())
                    .childOption(ChannelOption.SO_RCVBUF, serverConfig.getReceiveBufferSize())
                    .childHandler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel ch) {
                            initNettyChannel(ch);
                        }
                    });

            try {
                final ChannelFuture channelFuture = serverBootstrap.bind(serverConfig.getListenPort()).sync();
                if (channelFuture.isSuccess()) {
                    log.info("{}在绑定成功: {}", serverConfig.getServerName(), serverConfig.getListenPort());
                    this.serverBootstrapChannel = channelFuture.channel();
                } else {
                    throw new RemoteException(
                            String.format("%s bind %s fail", serverConfig.getServerName(),
                                    serverConfig.getListenPort()),
                            channelFuture.cause());
                }
            } catch (InterruptedException it) {
                ThreadUtils.rethrowInterruptedException(it);
            } catch (Exception e) {
                throw new RemoteException(
                        String.format("%s bind %s fail", serverConfig.getServerName(), serverConfig.getListenPort()),
                        e);
            }
        }
    }

    /**
     * 初始化Netty管道（责任链模式）
     * <p>管道配置顺序：
     * 1. 编码器 对象 → 字节流（TransporterEncoder）
     * 2. 解码器 字节流 → 对象（TransporterDecoder）
     * 3. 空闲检测 5000ms未收到请求则断开连接（IdleStateHandler）
     * 4. 业务处理器 实际执行RPC调用（JdkDynamicServerHandler）
     */
    private void initNettyChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast("encoder", new TransporterEncoder()) // 编码节点
                .addLast("decoder", new TransporterDecoder()) // 解码节点
                .addLast("server-idle-handle",      // 空闲检测节点
                        new IdleStateHandler(serverConfig.getConnectionIdleTime(), 0, 0, TimeUnit.MILLISECONDS))
                .addLast("handler", channelHandler); // 业务处理节点
    }

    /**
     * 注册方法调用器（观察者模式）
     *
     * @param methodInvoker RPC方法调用实现
     *                      <ul>
     *                        <li>methodIdentify: 方法唯一标识</li>
     *                        <li>methodProvider: 方法实现实例</li>
     *                        <li>methodMetadata: 方法元数据</li>
     *                      </ul>
     */
    void registerMethodInvoker(ServerMethodInvoker methodInvoker) {
        channelHandler.registerMethodInvoker(methodInvoker);
    }

    /**
     * 关闭服务端资源（资源池模式）
     * <p>执行操作：
     * 1. 关闭监听端口
     * 2. 优雅关闭线程组
     * 3. 终止方法调用线程池
     * <p>关闭顺序：
     * 1. 关闭监听端口
     * 2. 优雅关闭boss/work线程组
     * 3. 强制关闭方法调用线程池
     *
     * <p>设计特性：
     * 1. 幂等性：多次调用安全
     * 2. 资源泄漏防护：finally块保证关闭逻辑
     */
    void close() {
        if (isStarted.compareAndSet(true, false)) {
            log.info("{} closing", serverConfig.getServerName());
            try {
                if (serverBootstrapChannel != null) {
                    serverBootstrapChannel.close().sync();
                    log.info("{} stop bind at port: {}", serverConfig.getServerName(), serverConfig.getListenPort());
                }
                if (bossGroup != null) {
                    this.bossGroup.shutdownGracefully();
                }
                if (workGroup != null) {
                    this.workGroup.shutdownGracefully();
                }
                methodInvokerExecutor.shutdownNow();
            } catch (InterruptedException it) {
                ThreadUtils.consumeInterruptedException(it);
            } catch (Exception ex) {
                log.error("{} close failed", serverConfig.getServerName(), ex);
            }
            log.info("{} closed", serverConfig.getServerName());
        }
    }
}
