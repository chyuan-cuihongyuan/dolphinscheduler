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

package org.apache.dolphinscheduler.server.master.cluster.loadbalancer;

import org.apache.dolphinscheduler.server.master.cluster.WorkerClusters;

import java.util.Optional;

import lombok.NonNull;

/**
 * The worker load balancer used to select a worker from the {@link WorkerClusters} by load balancer algorithm.
 * 工作负载平衡器用于通过负载平衡器算法从 {@link WorkerClusters} 中选择工作负载。
 */
public interface IWorkerLoadBalancer {

    /**
     * Select a worker address under the given worker group.
     * 在给定的工作组下选择一个工作地址。
     *
     * @param workerGroup worker group cannot be null. 工作组不能为空。
     * @return the selected worker address, or empty if no worker is available. 所选的工作服务地址，如果没有工作服务可用，则为空。
     */
    Optional<String> select(@NonNull String workerGroup);

    /**
     * 获取工作负载平衡器类型
     *
     * @return 工作负载平衡器类型
     */
    WorkerLoadBalancerType getType();

}
