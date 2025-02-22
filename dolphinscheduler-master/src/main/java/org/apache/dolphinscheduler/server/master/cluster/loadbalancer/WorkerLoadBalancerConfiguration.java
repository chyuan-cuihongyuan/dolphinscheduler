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

import org.apache.dolphinscheduler.server.master.cluster.ClusterManager;
import org.apache.dolphinscheduler.server.master.config.MasterConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工作负载平衡器配置
 */
@Configuration
public class WorkerLoadBalancerConfiguration {

    /**
     * 创建工作负载均衡器实例
     *
     * @param masterConfig 主节点配置，用于获取负载均衡器配置属性
     * @param clusterManager 集群管理器，用于获取可用的工作节点集群信息
     * @return 根据配置类型创建的负载均衡器实例，实现IWorkerLoadBalancer接口
     * @throws IllegalArgumentException 当配置了不支持的负载均衡器类型时抛出异常
     */
    @Bean
    public IWorkerLoadBalancer randomWorkerLoadBalancer(MasterConfig masterConfig, ClusterManager clusterManager) {
        // 从主配置中获取负载均衡器配置属性
        WorkerLoadBalancerConfigurationProperties workerLoadBalancerConfigurationProperties =
                masterConfig.getWorkerLoadBalancerConfigurationProperties();
        // 根据配置类型创建对应的负载均衡器实现
        switch (workerLoadBalancerConfigurationProperties.getType()) {
            case RANDOM:
                // 创建随机选择策略的负载均衡器
                return new RandomWorkerLoadBalancer(clusterManager.getWorkerClusters());
            case ROUND_ROBIN:
                // 创建轮询策略的负载均衡器
                return new RoundRobinWorkerLoadBalancer(clusterManager.getWorkerClusters());
            case FIXED_WEIGHTED_ROUND_ROBIN:
                // 创建固定权重轮询策略的负载均衡器
                return new FixedWeightedRoundRobinWorkerLoadBalancer(clusterManager.getWorkerClusters());
            case DYNAMIC_WEIGHTED_ROUND_ROBIN:
                // 创建动态权重轮询策略的负载均衡器（需提供动态权重配置）
                return new DynamicWeightedRoundRobinWorkerLoadBalancer(
                        clusterManager.getWorkerClusters(),
                        workerLoadBalancerConfigurationProperties.getDynamicWeightConfigProperties());
            default:
                // 处理未支持的负载均衡类型配置
                throw new IllegalArgumentException(
                        "unSupport worker load balancer type " + workerLoadBalancerConfigurationProperties.getType());
        }
    }

}
