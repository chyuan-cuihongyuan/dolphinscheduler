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

package org.apache.dolphinscheduler.server.master.exception;

/**
 * 任务执行线程创建异常（关键资源分配异常）
 *
 * 核心职责：
 * 1. 标识任务执行线程(Runnable)实例化过程中的关键故障
 * 2. 暴露线程池资源管理或任务包装逻辑的缺陷
 * 3. 提供任务执行单元创建失败的关键诊断信息
 *
 * 典型触发场景：
 * - 线程池资源耗尽（拒绝新任务提交）
 * - 任务包装逻辑存在缺陷（空指针等）
 * - 任务元数据不完整（缺少必要执行参数）
 * - 执行上下文构建失败（资源分配超限）
 *
 * 异常处理策略：
 * 1. 立即终止当前任务调度尝试
 * 2. 触发资源回收流程（如已分配资源）
 * 3. 记录线程池状态快照（活跃线程/队列深度等）
 * 4. 根据错误类型选择重试/熔断策略
 */
public class TaskExecuteRunnableCreateException extends MasterException {

    /**
     * 构造基础线程创建异常
     * @param message 必须包含：
     *                - 任务实例ID
     *                - 线程池名称/标识
     *                - 资源类型（CPU/内存/线程等）
     *                示例："Create runnable failed for task[ID=112] in pool[default], thread quota exhausted"
     */
    public TaskExecuteRunnableCreateException(String message) {
        super(message);
    }

    /**
     * 构造带根本原因的链式异常
     * @param message 技术摘要（建议结构化格式）
     * @param throwable 原始异常（如RejectedExecutionException）
     *                  典型类型：
     *                  - RejectedExecutionException：线程池拒绝
     *                  - NullPointerException：空参数
     *                  - IllegalArgumentException：非法参数
     *                  - ResourceExhaustedException：资源不足
     */
    public TaskExecuteRunnableCreateException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
