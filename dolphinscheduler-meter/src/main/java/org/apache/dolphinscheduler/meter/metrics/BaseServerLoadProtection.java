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

package org.apache.dolphinscheduler.meter.metrics;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 基础服务器负载保护实现类
 * <p>通过监控系统指标实现负载过载保护机制，包含以下检查项：
 * <ul>
 *   <li>系统CPU使用率</li>
 *   <li>JVM CPU使用率</li>
 *   <li>磁盘使用率</li>
 *   <li>系统内存使用率</li>
 * </ul>
 */
@Slf4j
@Data
public class BaseServerLoadProtection implements ServerLoadProtection {

    /** 是否启用负载保护（默认启用） */
    protected boolean enabled = true;

    /** 系统CPU使用率最大阈值（百分比，默认70%） */
    protected double maxSystemCpuUsagePercentageThresholds = 0.7;

    /** JVM进程CPU使用率最大阈值（百分比，默认70%） */
    protected double maxJvmCpuUsagePercentageThresholds = 0.7;

    /** 系统内存使用率最大阈值（百分比，默认70%） */
    protected double maxSystemMemoryUsagePercentageThresholds = 0.7;

    /** 磁盘使用率最大阈值（百分比，默认70%） */
    protected double maxDiskUsagePercentageThresholds = 0.7;

    /**
     * 判断服务器是否过载
     * @param systemMetrics 系统指标数据
     * @return 当任一指标超过阈值时返回true，表示需要启动保护机制；
     *         所有指标正常或未启用保护时返回false
     */
    @Override
    public boolean isOverload(SystemMetrics systemMetrics) {
        if (!enabled) {
            return false;
        }
        if (systemMetrics.getSystemCpuUsagePercentage() > maxSystemCpuUsagePercentageThresholds) {
            log.info(
                    "OverLoad: the system cpu usage: {} is over then the maxSystemCpuUsagePercentageThresholds {}",
                    systemMetrics.getSystemCpuUsagePercentage(), maxSystemCpuUsagePercentageThresholds);
            return true;
        }
        if (systemMetrics.getJvmCpuUsagePercentage() > maxJvmCpuUsagePercentageThresholds) {
            log.info(
                    "OverLoad: the jvm cpu usage: {} is over then the maxJvmCpuUsagePercentageThresholds {}",
                    systemMetrics.getJvmCpuUsagePercentage(), maxJvmCpuUsagePercentageThresholds);
            return true;
        }
        if (systemMetrics.getDiskUsedPercentage() > maxDiskUsagePercentageThresholds) {
            log.info("OverLoad: the DiskUsedPercentage: {} is over then the maxDiskUsagePercentageThresholds {}",
                    systemMetrics.getDiskUsedPercentage(), maxDiskUsagePercentageThresholds);
            return true;
        }
        if (systemMetrics.getSystemMemoryUsedPercentage() > maxSystemMemoryUsagePercentageThresholds) {
            log.info(
                    "OverLoad: the SystemMemoryUsedPercentage: {} is over then the maxSystemMemoryUsagePercentageThresholds {}",
                    systemMetrics.getSystemMemoryUsedPercentage(), maxSystemMemoryUsagePercentageThresholds);
            return true;
        }
        return false;
    }
}
