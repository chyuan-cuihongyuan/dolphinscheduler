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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于轮询算法的Worker节点负载均衡器实现类
 *
 * 核心功能：
 * 1. 以循环轮转方式均匀分配Worker节点请求
 * 2. 实现IWorkerLoadBalancer接口，提供标准负载均衡方法
 *
 * @see IWorkerLoadBalancer 负载均衡器标准接口
 * The worker load balancer used to select a worker from the {@link WorkerClusters} by round-robin algorithm.
 */
public class RoundRobinWorkerLoadBalancer implements IWorkerLoadBalancer {

    // Worker集群管理组件（用于获取实时节点列表）
    private final WorkerClusters workerClusters;

    // 原子计数器实现线程安全的轮询索引（CAS操作保证并发安全）
    private final AtomicInteger robinIndex = new AtomicInteger(0);

    //尝试改进 使用线程安全的ConcurrentHashMap维护不同WorkerGroup的独立计数器
    private final ConcurrentHashMap<String, AtomicInteger> groupIndexMap = new ConcurrentHashMap<>();

    /**
     * 构造函数注入集群管理组件
     * @param workerClusters 提供节点状态信息的集群管理对象
     */
    public RoundRobinWorkerLoadBalancer(WorkerClusters workerClusters) {
        this.workerClusters = workerClusters;
    }

    /**
     * 轮询选择算法实现
     * @param workerGroup 目标Worker组名称
     * @return Optional包装的节点地址，空表示无可用节点
     */
    @Override
    public Optional<String> select(@NotNull String workerGroup) {
        // 1. 获取当前健康的Worker节点列表
        List<String> workerServerAddresses = workerClusters.getNormalWorkerServerAddressByGroup(workerGroup);

        // 2. 空列表校验（防御性编程）
        if (CollectionUtils.isEmpty(workerServerAddresses)) {
            return Optional.empty();
        }
        // 3. 原子递增并取模计算当前索引（避免synchronized性能损耗）
//        return Optional.of(workerServerAddresses.get(robinIndex.getAndIncrement() % workerServerAddresses.size()));

        // 按workerGroup维护独立计数器 computeIfAbsent原子操作获取或创建计数器 Key：当前Worker组名称 若不存在则创建初始值为0的原子计数器
        AtomicInteger index = groupIndexMap.computeIfAbsent(
                workerGroup, k -> new AtomicInteger(0));

        // 解决不同workerGroup间的竞争问题 原子更新索引值并计算有效位置 获取对应节点地址
        return Optional.of(workerServerAddresses.get(
                index.getAndUpdate(i -> (i + 1) % workerServerAddresses.size())
        ));
    }

    // 明确标识负载均衡器类型
    @Override
    public WorkerLoadBalancerType getType() {
        return WorkerLoadBalancerType.ROUND_ROBIN;
    }
}