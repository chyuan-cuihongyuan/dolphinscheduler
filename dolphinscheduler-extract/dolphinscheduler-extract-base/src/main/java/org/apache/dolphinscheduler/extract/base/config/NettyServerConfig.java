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

package org.apache.dolphinscheduler.extract.base.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

/**
 * Netty服务端配置类（建造者模式实现）
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>网络参数封装</b>：集中管理Netty服务端配置项</li>
 *   <li><b>默认值管理</b>：提供生产环境推荐配置</li>
 *   <li><b>建造者支持</b>：通过@Builder实现链式构造</li>
 * </ol>
 *
 * <p>配置项说明：
 * <ul>
 *   <li>serverName: 服务实例标识（如MasterRpcServer/WorkerRpcServer）</li>
 *   <li>listenPort: 必须配置项，服务监听端口</li>
 *   <li>workerThread: 默认CPU核心数*2（IO密集型场景建议值）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NettyServerConfig {

    /**
     * 服务实例名称（服务发现标识）
     * <p>命名规范：模块名称 + "RpcServer"
     */
    private String serverName;

    /**
     * 等待连接队列长度（TCP参数）
     * <p>默认值：1024（Linux系统建议＞1024）
     */
    @Builder.Default
    private int soBacklog = 1024;

    /**
     * 启用Nagle算法优化（网络延迟与吞吐量平衡）
     * <p>建议值：true（禁用Nagle算法，适合低延迟场景）
     */
    @Builder.Default
    private boolean tcpNoDelay = true;

    /**
     * 保持长连接（减少TCP握手开销）
     * <p>建议值：true（适合频繁通信场景）
     */
    @Builder.Default
    private boolean soKeepalive = true;

    /**
     * 发送缓冲区大小（字节）
     * <p>默认值：64KB（应根据实际消息大小调整）
     */
    @Builder.Default
    private int sendBufferSize = 65535;

    /**
     * 接收缓冲区大小（字节）
     * <p>默认值：64KB（高带宽场景建议增大）
     */
    @Builder.Default
    private int receiveBufferSize = 65535;

    /**
     * IO工作线程数（主从Reactor模式）
     * <p>计算公式：CPU核心数 × 2（IO密集型场景推荐）
     * <p>调整建议：高并发场景可设置为CPU核心数 × 3
     */
    @Builder.Default
    private int workerThread = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * 连接空闲超时（毫秒）
     * <p>默认值：60000ms（1分钟无数据则关闭连接）
     * <p>心跳机制应小于此值（建议心跳间隔≤30s）
     */
    @Builder.Default
    private long connectionIdleTime = Duration.ofSeconds(60).toMillis();

    /**
     * 服务监听端口（必须配置项）
     * <p>配置要求：1024＜port＜65535，生产环境应通过启动参数指定
     */
    private int listenPort;

}
