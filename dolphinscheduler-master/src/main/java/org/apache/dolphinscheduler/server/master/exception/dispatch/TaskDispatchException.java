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

package org.apache.dolphinscheduler.server.master.exception.dispatch;

import org.apache.dolphinscheduler.server.master.exception.MasterException;

/**
 * 任务调度分发异常（业务级可恢复异常）
 *
 * 核心职责：
 * 1. 标识任务在调度分配过程中出现的业务逻辑错误
 * 2. 区分于系统级错误（如JVM内存溢出）
 * 3. 提供任务调度上下文的关键诊断信息
 *
 * 典型触发场景：
 * - 负载均衡器无法选择可用Worker节点
 * - Worker节点拒绝接受任务（容量已满）
 * - 任务参数校验失败
 * - RPC通信超时或失败
 *
 * 异常处理策略：
 * 1. 业务层捕获后应尝试重试（带退避策略）
 * 2. 记录详细操作日志（包含任务ID、Worker组等）
 * 3. 触发告警通知（当连续失败超过阈值）
 */
public class TaskDispatchException extends MasterException {

    /**
     * 构造基础任务调度异常
     * @param message 需包含以下要素：
     *                - 任务唯一标识（taskInstanceId）
     *                - 目标Worker组/集群
     *                - 失败阶段描述
     *                示例："Failed to dispatch task[ID=123] to workerGroup[data_team], no available nodes"
     */
    public TaskDispatchException(String message) {
        super(message);
    }

    /**
     * 构造带根本原因的链式异常
     * @param message 业务摘要（需机器可解析）
     * @param cause 原始异常（保留技术细节）
     *              典型类型：
     *              - LoadBalanceException：负载均衡失败
     *              - RpcException：网络通信问题
     *              - TimeoutException：操作超时
     */
    public TaskDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
