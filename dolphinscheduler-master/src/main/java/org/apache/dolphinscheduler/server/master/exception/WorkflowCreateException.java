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
 * 表示在创建工作流实例时发生的异常，继承自Master主节点异常基类。
 * <p>
 * 当DolphinScheduler的Master节点在创建工作流实例过程中遇到不可恢复的错误时抛出。
 * 常见触发场景包括但不限于：
 * <ul>
 *   <li>工作流定义文件解析失败（如JSON/YAML格式错误）</li>
 *   <li>工作流参数校验不通过（如循环依赖、非法参数值）</li>
 *   <li>依赖服务不可用（如元数据数据库连接失败）</li>
 *   <li>资源配额不足（如ZK节点创建失败）</li>
 *   <li>插件加载失败（如使用未注册的任务插件）</li>
 * </ul>
 *
 * <p>推荐处理方式：
 * <ul>
 *   <li>检查工作流定义文件的完整性和语法正确性</li>
 *   <li>验证输入参数的有效性和取值范围</li>
 *   <li>确认系统依赖服务（数据库、注册中心等）的可用性</li>
 *   <li>捕获异常后记录审计日志并通知运维系统</li>
 *   <li>对于可重试错误建议采用指数退避重试策略</li>
 * </ul>
 *
 * @see MasterException 父类异常，包含：
 *      <ul>
 *        <li>异常上下文构建能力</li>
 *        <li>异常链追踪支持</li>
 *        <li>统一错误码管理机制</li>
 *      </ul>
 */
public class WorkflowCreateException extends MasterException {

    /**
     * 构造包含详细错误信息的异常实例
     *
     * @param message 错误描述应包含：
     * <ul>
     *   <li>工作流唯一标识（如code/name）</li>
     *   <li>具体失败阶段（如"解析定义文件"）</li>
     *   <li>关键参数摘要（如版本号、租户ID）</li>
     * </ul>
     * 示例："创建工作流[电商数据分析]失败：检测到循环依赖（节点A->节点B->节点A）"
     */
    public WorkflowCreateException(String message) {
        super(message);
    }

    /**
     * 构造包含详细错误信息和根本原因的异常实例
     *
     * @param message 人类可读的错误描述
     * @param throwable 原始异常（如IO异常、SQL异常等），用于：
     * <ul>
     *   <li>保留完整的异常堆栈信息</li>
     *   <li>支持异常链分析</li>
     *   <li>便于日志系统聚合根因</li>
     * </ul>
     * 示例："创建工作流[ID:456]上下文失败：ZK节点创建超时"，携带ZookeeperException
     */
    public WorkflowCreateException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
