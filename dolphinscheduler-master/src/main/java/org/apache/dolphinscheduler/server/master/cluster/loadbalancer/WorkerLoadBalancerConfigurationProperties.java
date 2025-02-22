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

import lombok.Data;

import org.springframework.validation.Errors;

/**
 * 工作节点负载均衡器配置属性类
 * <p>
 * 包含负载均衡算法类型选择及动态权重相关配置参数
 */
@Data
public class WorkerLoadBalancerConfigurationProperties {

    /**
     * 负载均衡算法类型，默认使用轮询策略（ROUND_ROBIN）
     */
    private WorkerLoadBalancerType type = WorkerLoadBalancerType.ROUND_ROBIN; // 工作负载均衡类型： 轮询

    /**
     * 动态权重计算相关配置参数
     */
    private DynamicWeightConfigProperties dynamicWeightConfigProperties = new DynamicWeightConfigProperties();

    /**
     * 执行配置参数校验
     *
     * @param errors Spring框架的错误收集对象，用于存储校验不通过的信息
     */
    public void validate(Errors errors) {
        dynamicWeightConfigProperties.validated(errors);
    }

    /**
     * 动态权重计算配置参数
     * <p>
     * 定义CPU、内存、任务线程池使用率在权重计算中的占比，
     * 各权重之和必须等于100%
     */
    @Data
    public static class DynamicWeightConfigProperties {

        /**
         * CPU使用率在权重计算中的占比，默认30%
         */
        private int cpuUsageWeight = 30;

        /**
         * 内存使用率在权重计算中的占比，默认30%
         */
        private int memoryUsageWeight = 30;

        /**
         * 任务线程池使用率在权重计算中的占比，默认40%
         */
        private int taskThreadPoolUsageWeight = 40;

        /**
         * 校验权重参数有效性
         *
         * @param errors Spring框架的错误收集对象，校验失败信息将存入该对象
         */
        public void validated(Errors errors) {
            if (cpuUsageWeight < 0) {
                // errors.rejectValue("cpuUsageWeight", "cpuUsageWeight", "cpuUsageWeight must >= 0");
                errors.rejectValue("cpu使用权重", "cpu使用权重", "cpu使用权重必须 >= 0");
            }
            if (memoryUsageWeight < 0) {
                // errors.rejectValue("memoryUsageWeight", "memoryUsageWeight", "memoryUsageWeight must >= 0");
                errors.rejectValue("内存使用权重", "内存使用权重", "内存使用权重 >= 0");
            }
            if (taskThreadPoolUsageWeight < 0) {
                // errors.rejectValue("threadUsageWeight", "threadUsageWeight", "threadUsageWeight must >= 0");
                errors.rejectValue("线程使用权重", "任务线程池使用权重", "线程使用权重 >= 0");
            }
            if (cpuUsageWeight + memoryUsageWeight + taskThreadPoolUsageWeight != 100) {
                // errors.rejectValue("cpuUsageWeight", "cpuUsageWeight",
                // "cpuUsageWeight + memoryUsageWeight + threadUsageWeight must be 100");
                errors.rejectValue("cpu使用权重", "cpu使用权重",
                        "cpu使用权重 + 内存使用权重 + 任务线程池使用权重 必须为100");
            }
        }

    }
}
