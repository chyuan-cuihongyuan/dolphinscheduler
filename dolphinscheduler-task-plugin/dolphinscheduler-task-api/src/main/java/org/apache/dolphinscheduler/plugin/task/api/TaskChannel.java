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

package org.apache.dolphinscheduler.plugin.task.api;

import org.apache.dolphinscheduler.plugin.task.api.parameters.AbstractParameters;

/**
 * 任务通道接口（工厂方法模式 + 桥接模式）
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>任务实例创建</b>：根据执行上下文生产具体任务处理器</li>
 *   <li><b>参数桥接转换</b>：将JSON参数转换为领域对象</li>
 * </ol>
 *
 * <p>设计约束：
 * <ul>
 *   <li>每个TaskChannel实现对应一种任务类型</li>
 *   <li>必须线程安全（通常实现为无状态对象）</li>
 * </ul>
 */
public interface TaskChannel {

    /**
     * 创建任务处理器实例（工厂方法模式）
     * @param taskRequest 任务执行上下文，包含：
     * <ul>
     *   <li>任务实例ID</li>
     *   <li>工作流实例信息</li>
     *   <li>资源文件列表</li>
     * </ul>
     * @return 具体任务处理器（如ShellTask、SqlTask）
     */
    AbstractTask createTask(TaskExecutionContext taskRequest);

    /**
     * 解析任务参数（桥接模式实现）
     * @param taskParams JSON格式的任务参数
     * @return 参数领域对象（如ShellParameters/SqlParameters）
     *
     * <p>异常处理要求：
     * <ul>
     *   <li>JSON解析失败应抛出IllegalArgumentException</li>
     *   <li>参数校验失败应记录错误日志</li>
     * </ul>
     */
    AbstractParameters parseParameters(String taskParams);

}
