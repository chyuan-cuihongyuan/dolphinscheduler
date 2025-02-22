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

package org.apache.dolphinscheduler.common.model;

import org.apache.dolphinscheduler.common.enums.ServerStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 基础心跳
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BaseHeartBeat implements HeartBeat {

    // 进程id
    protected int processId;
    // 服务启动时间
    protected long startupTime;
    // 上报时间
    protected long reportTime;
    // jvm cpu使用率
    protected double jvmCpuUsage;
    // 系统cpu使用率
    protected double cpuUsage;
    // jvm内存使用率
    protected double jvmMemoryUsage;
    // 系统内存使用率
    protected double memoryUsage;
    // 磁盘使用率
    protected double diskUsage;
    // 服务状态
    protected ServerStatus serverStatus;

    // 服务地址
    protected String host;
    // 服务端口
    protected int port;

}
