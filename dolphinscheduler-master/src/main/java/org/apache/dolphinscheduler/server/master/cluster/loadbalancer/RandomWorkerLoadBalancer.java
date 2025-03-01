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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.dolphinscheduler.server.master.cluster.WorkerClusters;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

/**
 * 基于随机算法的Worker节点负载均衡器实现类
 *
 * 核心功能：
 * 1. 从指定Worker组中随机选择一个可用Worker节点地址
 * 2. 实现IWorkerLoadBalancer接口，提供负载均衡标准方法
 *
 * @see IWorkerLoadBalancer 负载均衡器标准接口
 * The worker load balancer used to select a worker from the {@link WorkerClusters} by random algorithm.
 */
public class RandomWorkerLoadBalancer implements IWorkerLoadBalancer {
    // 依赖注入的Worker集群管理组件
    private final WorkerClusters workerClusters;

    // 使用安全随机数生成器（相比Random类具有更好的随机性）
    private final SecureRandom secureRandom;

    /**
     * 构造函数注入Worker集群实例
     * @param workerClusters 集群管理组件，用于获取节点信息
     */
    public RandomWorkerLoadBalancer(WorkerClusters workerClusters) {
        this.workerClusters = workerClusters;
        this.secureRandom = new SecureRandom(); // 初始化线程安全的随机数生成器
    }

    /**
     * 负载均衡选择算法实现
     * @param workerGroup 需要选择节点的Worker组名称
     * @return Optional包装的节点地址，空表示无可用节点
     */
    @Override
    public Optional<String> select(@NotNull String workerGroup) {
        // 1. 获取指定Worker组的正常节点地址列表
        List<String> workerServerAddresses = workerClusters.getNormalWorkerServerAddressByGroup(workerGroup);

        // 2. 空列表校验（防御性编程）
        if (CollectionUtils.isEmpty(workerServerAddresses)) {
            return Optional.empty();
        }

        // 3. 生成随机索引（范围：[0, size)）
        int index = secureRandom.nextInt(workerServerAddresses.size());

        // 4. 返回随机选择的节点地址
        return Optional.of(workerServerAddresses.get(index));
    }

    // 标识当前负载均衡器类型为RANDOM
    @Override
    public WorkerLoadBalancerType getType() {
        return WorkerLoadBalancerType.RANDOM;
    }
}
