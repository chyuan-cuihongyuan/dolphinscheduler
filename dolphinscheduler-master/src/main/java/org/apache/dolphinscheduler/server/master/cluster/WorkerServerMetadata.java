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

import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.common.model.WorkerHeartBeat;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 工作服务器元数据
 */
@Data
@SuperBuilder // 可以调用WorkerServerMetadata.builder()进行构造器
@EqualsAndHashCode(callSuper = true)
public class WorkerServerMetadata extends BaseServerMetadata {

    // 工作组
    @Builder.Default // 调用WorkerServerMetadata.builder()时的默认值
    private final String workerGroup = "default";

    // Only used in FixedWeightedRoundRobinWorkerLoadBalancer
    // 工作权重 仅用于FixedWeightedRoundRobinWorkerLoadBalancer
    @Builder.Default
    private final double workerWeight = 1;

    // 任务线程池使用率
    private final double taskThreadPoolUsage;

    /**
     * 从心跳中解析工作服务器元数据
     * @param workerHeartBeat 工作主机心跳
     * @return 工作服务器元数据
     */
    public static WorkerServerMetadata parseFromHeartBeat(final WorkerHeartBeat workerHeartBeat) {
        return WorkerServerMetadata.builder()
                .processId(workerHeartBeat.getProcessId())
                .serverStartupTime(workerHeartBeat.getStartupTime())
                .address(workerHeartBeat.getHost() + Constants.COLON + workerHeartBeat.getPort())
                .workerGroup(workerHeartBeat.getWorkerGroup())
                .cpuUsage(workerHeartBeat.getCpuUsage())
                .memoryUsage(workerHeartBeat.getMemoryUsage())
                .serverStatus(workerHeartBeat.getServerStatus())
                .workerWeight(workerHeartBeat.getWorkerHostWeight())
                .taskThreadPoolUsage(workerHeartBeat.getThreadPoolUsage())
                .build();
    }

}
