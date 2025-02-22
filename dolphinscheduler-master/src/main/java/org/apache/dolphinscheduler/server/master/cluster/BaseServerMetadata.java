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

package org.apache.dolphinscheduler.server.master.cluster;

import org.apache.dolphinscheduler.common.enums.ServerStatus;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 基本服务器元数据
 */
@Data
@ToString
@SuperBuilder
public abstract class BaseServerMetadata implements IClusters.IServerMetadata {

    // 进程id
    private final int processId;

    // The server startup time in milliseconds. 服务器启动时间（毫秒）。
    private final long serverStartupTime;

    // 地址
    private final String address;

    // CPU 使用率
    private final double cpuUsage;

    // 内存 使用率
    private final double memoryUsage;

    // 服务器状态
    private final ServerStatus serverStatus;

    /**
     * 获取地址
     *
     * @return 地址
     */
    @Override
    public String getAddress() {
        return address;
    }

    /**
     * 获取服务器状态
     *
     * @return 服务器状态
     */
    @Override
    public ServerStatus getServerStatus() {
        return serverStatus;
    }

}
