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
 * 逻辑任务初始化异常（关键运行时异常）
 *
 * 核心职责：
 * 1. 标识任务实例化过程中的业务逻辑错误
 * 2. 暴露任务定义与运行时环境的不兼容问题
 * 3. 提供任务初始化失败的关键诊断上下文
 *
 * 典型触发场景：
 * - 任务参数校验失败（如缺失必要配置项）
 * - 资源申请超限（CPU/Memory配额不足）
 * - 依赖服务不可用（数据库连接失败）
 * - 插件初始化异常（自定义组件加载失败）
 *
 * 异常处理策略：
 * 1. 立即终止当前任务调度流程
 * 2. 记录完整环境快照（参数/配置/资源状态）
 * 3. 触发告警通知（影响SLA的关键路径）
 */
public class LogicTaskInitializeException extends MasterException {

    /**
     * 构造基础初始化异常
     * @param message 必须包含：
     *                - 任务实例ID
     *                - 失败阶段（参数校验/资源分配等）
     *                - 具体错误描述
     *                示例："Initialize task[ID=456] failed: Invalid parameter 'scriptPath'"
     */
    public LogicTaskInitializeException(String message) {
        super(message);
    }

    /**
     * 构造链式异常（推荐用法）
     * @param message 技术摘要（需机器可解析）
     * @param cause 原始异常（保留完整堆栈）
     *              典型类型：
     *              - IllegalArgumentException：参数校验失败
     *              - ResourceException：资源分配失败
     *              - PluginLoadException：插件加载失败
     */
    public LogicTaskInitializeException(String message, Throwable cause) {
        super(message, cause);
    }

}
