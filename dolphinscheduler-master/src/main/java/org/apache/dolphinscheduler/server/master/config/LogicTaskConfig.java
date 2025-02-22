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

package org.apache.dolphinscheduler.server.master.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 逻辑任务执行线程配置类，用于管理任务执行线程池相关配置
 *
 * <p>采用构建器模式设计，支持链式调用配置参数</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LogicTaskConfig {

    /**
     * 任务执行线程池大小（默认值：CPU核心数*2+1）
     * <p>该配置用于控制同时执行的任务处理线程数量，默认采用与CPU核数相关的弹性配置策略</p>
     */
    @Builder.Default
    private int taskExecutorThreadCount = Runtime.getRuntime().availableProcessors() * 2 + 1;
}
