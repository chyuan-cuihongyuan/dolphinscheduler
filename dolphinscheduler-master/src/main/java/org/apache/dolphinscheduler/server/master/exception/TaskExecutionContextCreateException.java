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
 * 表示在创建任务执行上下文时发生的异常，继承自Master主节点异常基类。
 * <p>
 * 当DolphinScheduler的Master节点在尝试为任务创建执行上下文过程中遇到不可恢复的错误时抛出。
 * 常见触发场景包括但不限于：
 * <ul>
 *   <li>任务配置参数不合法（如缺失必要参数）</li>
 *   <li>资源分配失败（如CPU/内存资源不足）</li>
 *   <li>依赖服务不可用（如注册中心连接失败）</li>
 *   <li>工作流定义与任务定义不一致</li>
 * </ul>
 *
 * <p>该异常建议处理方式：
 * <ul>
 *   <li>检查任务配置参数的完整性和合法性</li>
 *   <li>确认系统资源可用性</li>
 *   <li>验证上下游服务状态</li>
 *   <li>捕获异常后进行重试或告警通知</li>
 * </ul>
 *
 * @see MasterException 父类异常，包含Master节点基础异常处理逻辑
 */
public class TaskExecutionContextCreateException extends MasterException {

    /**
     * 构造包含详细错误信息的异常实例
     *
     * @param message 具体的错误描述信息，应包含：
     * <ul>
     *   <li>失败的任务/工作流标识信息</li>
     *   <li>具体的失败原因（如"资源配额不足"）</li>
     *   <li>相关配置项信息（如涉及参数错误时）</li>
     * </ul>
     * 示例："创建工作流[ID:123]的上下文失败：数据库连接池耗尽（maxPoolSize=50）"
     */
    public TaskExecutionContextCreateException(String message) {
        super(message);
    }

}
