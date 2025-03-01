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

import lombok.extern.slf4j.Slf4j;
import org.apache.dolphinscheduler.extract.base.RpcMethod;
import org.apache.dolphinscheduler.extract.base.RpcService;
import org.apache.dolphinscheduler.extract.base.config.NettyServerConfig;

import java.lang.reflect.Method;

/**
 * 基于Netty的RPC服务端核心类（外观模式+工厂模式）
 * <p>
 * 基于Netty的RpcServer。服务器将注册方法调用程序并向客户端提供服务。
 * 一旦服务器启动，它将监听端口并等待客户端连接。
 * 客户端可以使用RpcClient类与服务器进行通信。
 * <p>
 * 示例用法：
 * <pre>
 *          RpcServer rpcServer = new RpcServer(new NettyServerConfig());
 *          rpcServer.registerServerMethodInvokerProvider(new ServerMethodInvokerProviderImpl());
 *          rpcServer.start();
 * </pre>
 *
 * <p>核心功能：
 * <ol>
 *   <li><b>服务注册</b>：通过注解驱动方式注册RPC服务方法</li>
 *   <li><b>网络通信</b>：封装Netty底层通信细节，提供简洁API</li>
 *   <li><b>生命周期管理</b>：实现AutoCloseable接口支持资源自动释放</li>
 * </ol>
 *
 * <p>应用设计模式：
 * <ul>
 *   <li><b>外观模式</b>：封装NettyRemotingServer的复杂操作</li>
 *   <li><b>工厂模式</b>：通过NettyRemotingServerFactory创建实例</li>
 *   <li><b>代理模式</b>：动态注册服务方法调用器</li>
 * </ul>
 */
@Slf4j
public class RpcServer implements ServerMethodInvokerRegistry, AutoCloseable {
    // 底层Netty服务器实例
    private final NettyRemotingServer nettyRemotingServer;

    /**
     * 构造方法（工厂模式实现）
     * @param nettyServerConfig Netty服务器配置对象
     * @see NettyRemotingServerFactory 实际创建实例的工厂类
     */
    public RpcServer(NettyServerConfig nettyServerConfig) {
        this.nettyRemotingServer = NettyRemotingServerFactory.buildNettyRemotingServer(nettyServerConfig);
    }

    /**
     * 启动RPC服务端（生命周期控制）
     * <p>执行流程：
     * 1. 初始化Netty事件循环组
     * 2. 绑定监听端口
     * 3. 启动IO线程
     */
    public void start() {
        nettyRemotingServer.start();
    }

    /**
     * 注册服务方法调用器（注解驱动注册）
     * <p>实现机制：
     * 1. 扫描接口上的@RpcService注解
     * 2. 发现@RpcMethod标注的方法
     * 3. 构建方法调用器并注册到Netty服务
     *
     * @param serverMethodInvokerProviderBean 包含RPC服务方法的Bean实例
     */
    @Override
    public void registerServerMethodInvokerProvider(Object serverMethodInvokerProviderBean) {
        for (Class<?> anInterface : serverMethodInvokerProviderBean.getClass().getInterfaces()) {
            if (anInterface.getAnnotation(RpcService.class) == null) {
                continue;
            }
            for (Method method : anInterface.getDeclaredMethods()) {
                RpcMethod rpcMethod = method.getAnnotation(RpcMethod.class);
                if (rpcMethod == null) {
                    continue;
                }
                ServerMethodInvoker serverMethodInvoker =
                        new ServerMethodInvokerImpl(serverMethodInvokerProviderBean, method);
                nettyRemotingServer.registerMethodInvoker(serverMethodInvoker);
                log.debug("注册 ServerMethodInvoker: {} to bean: {}",
                        serverMethodInvoker.getMethodIdentify(), serverMethodInvoker.getMethodProviderIdentify());
            }
        }
    }

    /**
     * 关闭RPC服务端（资源释放）
     * <p>执行操作：
     * 1. 关闭Netty Channel
     * 2. 释放线程池资源
     * 3. 注销所有注册的方法调用器
     */
    @Override
    public void close() {
        nettyRemotingServer.close();
    }
}
