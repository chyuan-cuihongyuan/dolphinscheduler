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
 * 动态加权平滑轮询负载均衡器实现
 *
 * 核心创新点：
 * 1. 实时权重计算：基于服务器实时负载指标（CPU/内存/线程池使用率）动态调整权重
 * 2. 多维度权重配置：支持通过配置策略调整各指标的权重占比
 * 3. 热更新机制：通过集群监听实现节点状态实时同步
 *
 * This load balancer is used to select a worker from {@link WorkerClusters} by dynamic weights.
 * </p>
 * The dynamic weights are calculated by the worker's load. e.g. cpu/memory/disk usage/thread usage etc.
 * You can config the weight calculation strategy in {@link WorkerLoadBalancerConfigurationProperties.DynamicWeightConfigProperties}.
 * 此负载平衡器用于通过动态权重从{@link WorkerClusters}中选择工作人员。
 * 动态权重根据工人的负载计算。例如cpu/ 内存/ 磁盘使用率/ 线程使用率等。您可以在 {@link WorkerLoadBalancerConfigurationProperties.DynamicWeightConfigProperties}中配置权重计算策略。动态权重配置属性。
 */
public class DynamicWeightedRoundRobinWorkerLoadBalancer implements IWorkerLoadBalancer {

    // 集群管理组件（含节点健康状态）
    private final WorkerClusters workerClusters;

    // 全局轮询索引（所有workerGroup共享）
    private final AtomicInteger robinIndex = new AtomicInteger(0);

    // 权重元数据存储（地址->权重对象）
    private Map<String, WeightedServer<WorkerServerMetadata>> weightedServerMap = new ConcurrentHashMap<>();

    /**
     * 构造函数初始化动态权重监听
     * @param workerClusters 集群管理组件
     * @param dynamicWeightConfigProperties 权重计算策略配置
     */
    public DynamicWeightedRoundRobinWorkerLoadBalancer(WorkerClusters workerClusters,
                                                       WorkerLoadBalancerConfigurationProperties.DynamicWeightConfigProperties dynamicWeightConfigProperties) {
        this.workerClusters = workerClusters;
        // 注册三态监听器
        this.workerClusters.registerListener(new IClusters.IClustersChangeListener<WorkerServerMetadata>() {

            /**
             * 服务器添加 节点上线时初始化权重
             * @param server 服务器
             */
            @Override
            public void onServerAdded(WorkerServerMetadata server) {
                weightedServerMap.put(server.getAddress(), new WeightedServer<>(server, calculateWeight(server)));
            }

            /**
             * 服务器删除  节点下线时移除权重记录
             * @param server 服务器
             */
            @Override
            public void onServerRemove(WorkerServerMetadata server) {
                weightedServerMap.remove(server.getAddress());
            }

            /**
             * 服务器更新 节点状态更新时重新计算权重
             * @param server 服务器
             */
            @Override
            public void onServerUpdate(WorkerServerMetadata server) {
                weightedServerMap.put(server.getAddress(), new WeightedServer<>(server, calculateWeight(server)));
            }

            /**
             * 计算权重  权重计算公式（示例：100 - (CPU权重*使用率 + 内存权重*使用率 + 线程池权重*使用率)/3）
             * @param server 服务器
             * @return double
             */
            private double calculateWeight(WorkerServerMetadata server) {
                return 100 - (dynamicWeightConfigProperties.getCpuUsageWeight() * server.getCpuUsage()
                        + dynamicWeightConfigProperties.getMemoryUsageWeight() * server.getMemoryUsage()
                        + dynamicWeightConfigProperties.getTaskThreadPoolUsageWeight()
                                * server.getTaskThreadPoolUsage())
                        / 3;
            }
        });
    }

    /**
     * 使用加权轮询算法选择指定工作组中的可用服务器地址
     *
     * 实现逻辑：
     * 1. 根据工作组获取正常服务器列表，并过滤出有效权重配置
     * 2. 计算服务器总权重值
     * 3. 通过动态权重调整算法选择最终服务器节点
     *
     * @param workerGroup 需要选择服务器的工作组名称 worker group cannot be null. 工作组不能为空。
     * @return Optional包装的服务器地址，当无可用服务器时返回空Optional
     */
    @Override
    public Optional<String> select(@NotNull String workerGroup) {
        // 1. 获取有效节点列表（带最新权重）
        List<WeightedServer<WorkerServerMetadata>> weightedServers =
                workerClusters.getNormalWorkerServerAddressByGroup(workerGroup)
                        .stream()
                        .map(weightedServerMap::get)
                        .filter(Objects::nonNull)
                        // 过滤空值以避免两个非原子操作的Map（workerClusters和weightedServerMap）之间数据不一致
                        // filter non null here to avoid the two map changed between 在此处过滤非空值，以避免两个映射之间发生变化
                        // workerClusters 和
                        // weightedServerMap不是原子的
                        .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(weightedServers)) {
            return Optional.empty();
        }

        // 计算所有服务器的总权重值，用于后续权重调整
        double totalWeight = weightedServers.stream().mapToDouble(WeightedServer::getWeight).sum();

        WeightedServer<WorkerServerMetadata> selectedWorker = null;
        // 动态权重调整循环：通过当前权重累计和总权重比较进行选择
        while (selectedWorker == null) {
            // 1. 轮询选择节点
            // 使用原子类保证线程安全，递增索引并取模获得循环访问的节点位置
            WeightedServer<WorkerServerMetadata> tmpWorker =
                    weightedServers.get((robinIndex.incrementAndGet()) % weightedServers.size());
            // 2. 动态调整权重
            // 累加该节点的基础权重到当前权重，模拟流量累积效果
            tmpWorker.setCurrentWeight(tmpWorker.getCurrentWeight() + tmpWorker.getWeight());

            // 当前权重大于等于总权重时选定节点，并重置当前权重
            // 3. 权重判定逻辑
            // 当累积权重超过总权重阈值时选中该节点（体现高权重节点的优先选择）
            if (tmpWorker.getCurrentWeight() >= totalWeight) {
                // 4. 权重重置机制
                // 扣减总权重值保持权重系统的动态平衡，为下一轮选择做准备
                tmpWorker.setCurrentWeight(tmpWorker.getCurrentWeight() - totalWeight);
                selectedWorker = tmpWorker; // 完成节点选择
            }
        }

        return Optional.of(selectedWorker.getServer().getAddress());
    }

    /**
     * 获取工作负载平衡器类型
     *
     * @return 工作负载平衡器类型
     */
    @Override
    public WorkerLoadBalancerType getType() {
        return WorkerLoadBalancerType.DYNAMIC_WEIGHTED_ROUND_ROBIN;
    }
}
