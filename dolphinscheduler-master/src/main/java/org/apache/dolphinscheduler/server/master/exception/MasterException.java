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
 * Master节点专属业务异常基类
 *
 * 设计定位：
 * 1. 统一封装Master服务核心流程中的可恢复性业务异常
 * 2. 区分系统级错误（Error）与业务级异常（Exception）
 * 3. 提供异常信息结构化传递能力
 *
 * 继承体系：
 * Throwable
 *   → Exception
 *     → MasterException
 *       → 各具体业务异常（如TaskDispatchException等）
 *
 * 使用规范：
 * - 适用于需要上游调用方显式处理的业务场景
 * - 携带明确的错误分类标识（建议扩展错误码体系）
 * - 保留原始异常堆栈信息
 */
public class MasterException extends Exception {
    /**
     * 构造基础业务异常
     * @param message 需包含以下要素：
     *                - 错误现象描述
     *                - 错误发生模块
     *                - 关键业务标识（如任务ID、流程实例ID等）
     *                示例："Failed to dispatch task[ID=123] in workflow[ID=456]"
     */
    public MasterException(String message) {
        super(message);
    }

    /**
     * 构造带根本原因的链式异常
     * @param message 业务层错误摘要（需简明可读）
     * @param throwable 原始异常（保留完整堆栈）
     *                  典型场景：
     *                  - RPC调用异常
     *                  - 数据库操作异常
     *                  - 第三方服务异常
     */
    public MasterException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
