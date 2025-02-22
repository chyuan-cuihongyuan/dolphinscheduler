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

/**
 * 服务端负载保护检测接口，用于判断系统当前是否处于高负载状态
 * <p>实现该接口的类需定义具体的负载评估算法（如基于CPU/内存使用率等系统指标）</p>
 */
public interface ServerLoadProtection {

    /**
     * 检测系统负载是否超过安全阈值
     * @param systemMetrics 系统指标数据（包含CPU、内存等运行时指标）
     * @return true表示系统过载需要触发保护机制，false表示系统负载正常
     */
    boolean isOverload(SystemMetrics systemMetrics);

}
