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

package org.apache.dolphinscheduler.server.master.engine.task.runnable;

import org.apache.dolphinscheduler.dao.entity.TaskDefinition;
import org.apache.dolphinscheduler.dao.entity.TaskInstance;
import org.apache.dolphinscheduler.dao.entity.WorkflowInstance;
import org.apache.dolphinscheduler.plugin.task.api.TaskExecutionContext;
import org.apache.dolphinscheduler.server.master.engine.WorkflowEventBus;
import org.apache.dolphinscheduler.server.master.engine.graph.IWorkflowExecutionGraph;
import org.apache.dolphinscheduler.server.master.engine.workflow.runnable.WorkflowExecutionRunnable;

/**
 * 任务执行运行接口，用于管理任务实例的生命周期操作和状态控制。
 * <p>
 * 本接口作为工作流引擎中任务执行的核心抽象，主要职责包括：
 * <ul>
 *   <li>管理任务实例的初始化、重试、故障转移等生命周期</li>
 *   <li>提供任务状态控制（暂停/终止）</li>
 *   <li>维护与工作流执行上下文的关联关系</li>
 *   <li>实现任务执行优先级控制（通过Comparable接口）</li>
 * </ul>
 * <p>
 * 通过实现Comparable接口：
 * 1. 支持任务优先级队列管理（如PriorityBlockingQueue）
 * 2. 确保任务按拓扑顺序执行（依赖关系）
 * 3. 实现基于时间/优先级的混合调度策略
 * <p>典型应用场景：
 * <ul>
 *   <li>工作流引擎启动任务实例时创建实现类</li>
 *   <li>任务失败时触发重试机制</li>
 *   <li>集群故障时执行故障转移恢复</li>
 *   <li>手动干预任务状态（暂停/终止）</li>
 * </ul>
 *
 * @see WorkflowExecutionRunnable 关联的工作流执行实体
 * @see Comparable 实现类之间的执行顺序比较，通常基于：
 * <ul>
 *   <li>任务优先级配置</li>
 *   <li>工作流节点依赖顺序</li>
 *   <li>任务创建时间戳</li>
 * </ul>
 */
public interface ITaskExecutionRunnable extends Comparable<ITaskExecutionRunnable> {

    /**
     * 获取任务实例ID（可能动态变化）
     * <p>
     * ⚠️ 注意：当发生任务重试或故障转移时，该方法返回的ID可能指向新生成的TaskInstance
     *
     * @return 当前关联的任务实例ID，可能为：
     * <ul>
     *   <li>首次运行的任务实例ID</li>
     *   <li>重试后生成的新任务实例ID</li>
     *   <li>故障转移恢复的任务实例ID</li>
     * </ul>
     */
    default int getId() {
        return getTaskInstance().getId();
    }

    /**
     * 获取任务定义名称（稳定标识）
     *
     * @return 任务定义配置中的名称，通常用于：
     * <ul>
     *   <li>日志追踪</li>
     *   <li>监控指标</li>
     *   <li>用户界面展示</li>
     * </ul>
     */
    default String getName() {
        return getTaskDefinition().getName();
    }

    /**
     * 校验任务实例是否完成初始化
     * <p>如果ITaskExecutionUnable从未被触发，则它不会被初始化
     * <p> 如果ITaskExecutionUnable是通过故障转移创建的，那么它将被初始化。
     *
     * @return true表示以下情况之一：
     * <ul>
     *   <li>任务已通过initializeFirstRunTaskInstance()初始化</li>
     *   <li>从故障转移中恢复的实例</li>
     * </ul>
     */
    boolean isTaskInstanceInitialized();

    /**
     * 初始化首运行任务实例
     * <p>
     * 通过{@link FirstRunTaskInstanceFactory}创建初始TaskInstance，
     * 典型调用场景：
     * <ul>
     *   <li>工作流首次执行时</li>
     *   <li>任务依赖条件满足时</li>
     * </ul>
     */
    void initializeFirstRunTaskInstance();

    /**
     * 判断任务是否可重试
     *
     * @return true需同时满足：
     * <ul>
     *   <li>任务配置允许重试（retryTimes > 0）</li>
     *   <li>当前重试次数未达上限</li>
     *   <li>任务处于可重试状态（如FAILURE）</li>
     * </ul>
     */
    boolean isTaskInstanceCanRetry();

    /**
     * 执行任务重试操作
     * <p>
     * 实现逻辑应包含：
     * <ol>
     *   <li>创建新的重试任务实例</li>
     *   <li>继承必要上下文（参数、依赖等）</li>
     *   <li>更新工作流执行图状态</li>
     *   <li>提交到任务队列重新执行</li>
     * </ol>
     */
    void retry();

    /**
     * 执行故障转移操作
     * <p>
     * 典型场景：
     * <ul>
     *   <li>Worker节点失联</li>
     *   <li>任务心跳丢失超时</li>
     *   <li>ZK会话过期</li>
     * </ul>
     * 实现逻辑应包含：
     * <ol>
     *   <li>判断任务实例状态是否允许故障转移</li>
     *   <li>重新分配Worker节点</li>
     *   <li>保持任务尝试次数不变</li>
     *   <li>提交到新Worker执行</li>
     * </ol>
     */
    void failover();

    /**
     * 暂停任务执行
     * <p>
     * 实现要求：
     * <ul>
     *   <li>若任务正在运行，应通知Worker停止执行</li>
     *   <li>更新任务状态为PAUSED</li>
     *   <li>记录暂停操作审计日志</li>
     * </ul>
     */
    void pause();

    /**
     * 终止任务执行
     * <p>
     * 强制终止逻辑应包含：
     * <ol>
     *   <li>向Worker发送kill命令</li>
     *   <li>等待Worker确认终止</li>
     *   <li>更新任务状态为KILLED</li>
     *   <li>清理任务占用的资源</li>
     * </ol>
     */
    void kill();

    // 以下为上下文获取方法群

    /**
     * 获取工作流事件总线
     *
     * @return 用于：
     * <ul>
     *   <li>发布任务状态变更事件</li>
     *   <li>监听工作流级事件（如超时通知）</li>
     * </ul>
     */
    WorkflowEventBus getWorkflowEventBus();

    /**
     * 获取工作流执行图
     *
     * @return 包含：
     * <ul>
     *   <li>任务节点依赖关系</li>
     *   <li>工作流执行状态</li>
     *   <li>上下游任务上下文</li>
     * </ul>
     */
    IWorkflowExecutionGraph getWorkflowExecutionGraph();

    /**
     * 获取关联的工作流实例
     */
    WorkflowInstance getWorkflowInstance();

    /**
     * 获取当前任务实例（可能为最新实例）
     */
    TaskInstance getTaskInstance();

    /**
     * 获取任务定义元数据
     */
    TaskDefinition getTaskDefinition();

    /**
     * 获取任务执行上下文
     *
     * @return 包含运行时信息：
     * <ul>
     *   <li>任务参数（包括动态替换后的值）</li>
     *   <li>资源配置（CPU/内存等）</li>
     *   <li>环境变量</li>
     * </ul>
     */
    TaskExecutionContext getTaskExecutionContext();
}
