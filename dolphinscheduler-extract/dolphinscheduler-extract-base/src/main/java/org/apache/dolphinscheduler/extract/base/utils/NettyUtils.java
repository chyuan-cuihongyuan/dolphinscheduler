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

package org.apache.dolphinscheduler.extract.base.utils;

import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * Netty通道工具类（工厂方法模式 + 环境适配模式）
 *
 * <p>核心功能：
 * <ul>
 *   <li><b>通道类型自动选择</b>：根据操作系统支持情况自动选择Epoll/NIO实现</li>
 *   <li><b>平台适配</b>：Linux系统优先使用Epoll提升性能，其他系统使用标准NIO</li>
 * </ul>
 *
 * <p>设计特性：
 * <ol>
 *   <li>单例模式：私有构造函数防止实例化</li>
 *   <li>环境检测：通过Epoll.isAvailable()判断系统支持性</li>
 * </ol>
 */
public class NettyUtils {

    /**
     * 私有构造器（单例模式保障）
     * <p>作用：防止工具类被实例化
     */
    private NettyUtils() {
    }

    /**
     * 获取服务端Socket通道类（工厂方法模式）
     * <p>选择策略：
     * <ul>
     *   <li>Linux内核≥2.6且开启epoll → EpollServerSocketChannel</li>
     *   <li>其他情况 → NioServerSocketChannel</li>
     * </ul>
     *
     * @return 最优化的ServerSocketChannel实现类
     */
    public static Class<? extends ServerSocketChannel> getServerSocketChannelClass() {
        if (Epoll.isAvailable()) {
            return EpollServerSocketChannel.class;
        }
        return NioServerSocketChannel.class;
    }

    /**
     * 获取客户端Socket通道类（环境适配模式）
     * <p>性能对比：
     * <ul>
     *   <li>EpollSocketChannel：减少GC压力，高并发场景更高效</li>
     *   <li>NioSocketChannel：跨平台通用实现</li>
     * </ul>
     */
    public static Class<? extends SocketChannel> getSocketChannelClass() {
        if (Epoll.isAvailable()) {
            return EpollSocketChannel.class;
        }
        return NioSocketChannel.class;
    }

}
