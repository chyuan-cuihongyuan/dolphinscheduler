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
import org.apache.dolphinscheduler.server.master.cluster.IClusters;
import org.apache.dolphinscheduler.server.master.cluster.WorkerClusters;
import org.apache.dolphinscheduler.server.master.cluster.WorkerServerMetadata;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 固定权重的平滑加权轮询负载均衡器实现
 *
 * 核心特性：
 * 1. 根据预设权重分配请求，权重高的节点获得更多流量
 * 2. 实现平滑加权轮询算法，避免传统加权轮询的请求突增问题
 * 3. 支持动态节点管理，自动感知节点上下线事件
 *
 * @see IWorkerLoadBalancer 负载均衡器标准接口
 * 此负载平衡器使用固定加权循环算法从｛@link WorkerClusters｝中选择一个worker
 * <p> 例如，如果有3个权重为1,2,3的工作服务器，则选择如下：1,2,3,1,2,3,3,1,2,3,3,1,2,3
 * <p> 每个工作服务器的权重由工作服务器本身决定。
 */
public class FixedWeightedRoundRobinWorkerLoadBalancer implements IWorkerLoadBalancer {

    // 集群管理组件（提供节点状态信息）
    private final WorkerClusters workerClusters;

    // 全局轮询索引（所有workerGroup共享，存在优化空间）
    private final AtomicInteger robinIndex = new AtomicInteger(0);

    // 权重节点元数据存储（地址 -> 权重对象）
    private final Map<String, WeightedServer<WorkerServerMetadata>> weightedServerMap = new ConcurrentHashMap<>();

    /**
     * 构造函数初始化集群监听
     * @param workerClusters 集群管理组件
     */
    public FixedWeightedRoundRobinWorkerLoadBalancer(WorkerClusters workerClusters) {
        this.workerClusters = workerClusters;
        // 注册节点变更监听器（实现动态权重维护）
        this.workerClusters.registerListener(new IClusters.IClustersChangeListener<WorkerServerMetadata>() {

            @Override
            public void onServerAdded(WorkerServerMetadata server) {
                // 节点上线时初始化权重记录
                weightedServerMap.put(server.getAddress(), new WeightedServer<>(server, server.getWorkerWeight()));
            }

            @Override
            public void onServerRemove(WorkerServerMetadata server) {
                // 存在问题的移除逻辑（建议改进点）
                weightedServerMap.remove(server.getAddress(), new WeightedServer<>(server, server.getWorkerWeight()));
            }

            @Override
            public void onServerUpdate(WorkerServerMetadata server) {
                // todo 监听到服务进行更新为什么不更新权重？ 服务更新不可以修改权重吗？
                // don't care the update event, since this will not affect the weight
            }
        });
    }

    /**
     * 加权轮询选择算法实现
     * @param workerGroup 目标Worker组名称
     * @return Optional包装的节点地址
     */
    @Override
    public Optional<String> select(@NotNull String workerGroup) {
        // 1. 获取当前有效节点列表（带权重信息）
        List<WeightedServer<WorkerServerMetadata>> weightedServers =
                workerClusters.getNormalWorkerServerAddressByGroup(workerGroup)
                        .stream()
                        // 地址转权重对象
                        .map(weightedServerMap::get)
                        // filter non null here to avoid the two map changed between
                        // workerClusters and weightedServerMap is not atomic
                        // 过滤已下线的节点
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(weightedServers)) {
            return Optional.empty();
        }
        // 2. 计算当前总权重（动态变化值）
        double totalWeight = weightedServers.stream().mapToDouble(WeightedServer::getWeight).sum();

        // 3. 平滑加权轮询算法核心逻辑
        WeightedServer<WorkerServerMetadata> selectedWorker = null;
        while (selectedWorker == null) {
            // 3.1 基于全局索引选择临时节点
            WeightedServer<WorkerServerMetadata> tmpWorker =
                    weightedServers.get((robinIndex.incrementAndGet()) % weightedServers.size());
            // 3.2 更新当前权重（模拟权重累积）
            tmpWorker.setCurrentWeight(tmpWorker.getCurrentWeight() + tmpWorker.getWeight());

            // 3.3 权重达标判定
            if (tmpWorker.getCurrentWeight() >= totalWeight) {
                tmpWorker.setCurrentWeight(tmpWorker.getCurrentWeight() - totalWeight);
                selectedWorker = tmpWorker;
            }
        }

        return Optional.of(selectedWorker.getServer().getAddress());
    }


    // 标识负载均衡器类型
    @Override
    public WorkerLoadBalancerType getType() {
        return WorkerLoadBalancerType.FIXED_WEIGHTED_ROUND_ROBIN;
    }

}
