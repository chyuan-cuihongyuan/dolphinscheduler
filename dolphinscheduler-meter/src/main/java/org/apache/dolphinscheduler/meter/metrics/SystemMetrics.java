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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统指标数据模型类，用于记录多维度的系统资源度量信息
 * <p>
 * 包含CPU、JVM内存、系统内存、磁盘等核心资源的使用指标，
 * 所有百分比类指标范围均为0-100%
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemMetrics {

    /* --------------- CPU 指标 --------------- */
    /**
     * 整个操作系统层面的CPU使用率百分比
     */
    private double systemCpuUsagePercentage;

    /**
     * 当前JVM进程占用的CPU使用率百分比
     */
    private double jvmCpuUsagePercentage;

    /* ------------ JVM内存指标 --------------- */
    // todo: get pod memory usage 获取pod内存使用情况
    /**
     * JVM已使用的内存量（单位取决于具体实现，通常为字节）
     */
    private double jvmMemoryUsed;

    /**
     * JVM可申请的最大内存量（单位同上）
     */
    private double jvmMemoryMax;

    /**
     * JVM内存使用率百分比（used/max）
     */
    private double jvmMemoryUsedPercentage;

    /* ------------ 系统内存指标 --------------- */
    // todo: get pod cpu usage 获取pod cpu使用率
    /**
     * 操作系统已使用的物理内存量
     */
    private double systemMemoryUsed;

    /**
     * 操作系统物理内存总量
     */
    private double systemMemoryMax;

    /**
     * 系统物理内存使用率百分比（used/max）
     */
    private double systemMemoryUsedPercentage;

    /* --------------- 磁盘指标 ---------------- */
    // todo: get pod disk usage
    /**
     * 磁盘已使用存储空间量
     */
    private double diskUsed;

    /**
     * 磁盘存储空间总量
     */
    private double diskTotal;

    /**
     * 磁盘空间使用率百分比（used/total）
     */
    private double diskUsedPercentage;
}
