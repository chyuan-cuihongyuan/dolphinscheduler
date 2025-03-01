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
 * Master节点任务执行异常（核心运行时异常）
 *
 * 核心职责：
 * 1. 标识任务在Master协调执行过程中的关键错误
 * 2. 暴露任务执行阶段的服务间协作问题
 * 3. 提供任务执行上下文的关键诊断信息
 *
 * 典型触发场景：
 * - 工作流依赖解析失败
 * - 任务状态机转换异常
 * - 分布式锁获取超时
 * - 任务结果处理失败
 *
 * 异常特征：
 * - 可能影响整个工作流的执行进度
 * - 需要结合系统全局状态进行诊断
 * - 通常需要人工介入修复数据
 */
public class MasterTaskExecuteException extends MasterException {

    /**
     * 构造基础执行异常
     * @param message 必须包含：
     *                - 工作流实例ID
     *                - 任务实例ID
     *                - 失败操作描述
     *                示例："Execute task[ID=789] in workflow[ID=123] failed: State transition invalid"
     */
    public MasterTaskExecuteException(String message) {
        super(message);
    }

    /**
     * 构造带根本原因的链式异常
     * @param message 技术摘要（JSON可解析格式建议）
     * @param cause 原始异常（保留技术堆栈）
     *              典型类型：
     *              - StateTransitionException：状态机异常
     *              - LockAcquireException：分布式锁问题
     *              - DataConsistencyException：数据不一致
     */
    public MasterTaskExecuteException(String message, Throwable cause) {
        super(message, cause);
    }
}
