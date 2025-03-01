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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.dolphinscheduler.dao.entity.*;
import org.apache.dolphinscheduler.plugin.task.api.TaskExecutionContext;
import org.apache.dolphinscheduler.server.master.engine.WorkflowEventBus;
import org.apache.dolphinscheduler.server.master.engine.graph.IWorkflowExecutionGraph;
import org.apache.dolphinscheduler.server.master.engine.task.client.ITaskExecutorClient;
import org.apache.dolphinscheduler.server.master.engine.task.lifecycle.event.TaskKillLifecycleEvent;
import org.apache.dolphinscheduler.server.master.engine.task.lifecycle.event.TaskPauseLifecycleEvent;
import org.apache.dolphinscheduler.server.master.engine.task.lifecycle.event.TaskStartLifecycleEvent;
import org.apache.dolphinscheduler.server.master.runner.TaskExecutionContextFactory;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * 任务执行运行实体，实现任务实例的生命周期管理和状态控制。
 * <p>
 * 作为工作流引擎中任务执行的核心实现类，主要职责包括：
 * <ul>
 *   <li>管理任务实例的全生命周期（初始化/重试/故障转移）</li>
 *   <li>处理任务状态变更事件（暂停/终止）</li>
 *   <li>维护任务执行上下文环境</li>
 *   <li>实现任务优先级调度逻辑</li>
 * </ul>
 *
 * <p>设计特征：
 * <ul>
 *   <li>线程安全：通过状态校验（checkState）保证操作原子性</li>
 *   <li>事件驱动：通过WorkflowEventBus发布生命周期事件</li>
 *   <li>上下文隔离：每个实例持有独立的任务执行上下文</li>
 *   <li>工厂模式：通过TaskInstanceFactories创建不同场景的任务实例</li>
 * </ul>
 *
 * @see ITaskExecutionRunnable 实现的接口定义
 * @see TaskExecutionRunnableBuilder 使用的建造者模式
 */
@Slf4j
public class TaskExecutionRunnable implements ITaskExecutionRunnable {

    // 依赖注入上下文（Spring容器）
    private final ApplicationContext applicationContext;

    // 执行图模型（维护节点依赖关系）
    @Getter
    private final IWorkflowExecutionGraph workflowExecutionGraph;

    // 工作流级事件总线（用于状态变更通知）
    @Getter
    private final WorkflowEventBus workflowEventBus;

    // 工作流定义元数据
    @Getter
    private final WorkflowDefinition workflowDefinition;

    // 项目上下文信息
    @Getter
    private final Project project;

    // 工作流运行时实例
    @Getter
    private final WorkflowInstance workflowInstance;

    // 当前任务实例（可能随重试/故障转移变化）
    @Getter
    private @Nullable TaskInstance taskInstance;

    // 任务定义元数据（稳定不变）
    @Getter
    private final TaskDefinition taskDefinition;

    // 任务执行上下文（包含运行时参数）
    @Getter
    private TaskExecutionContext taskExecutionContext;

    /**
     * 构造方法（建造者模式）
     *
     * @param taskExecutionRunnableBuilder 建造者对象，需包含：
     *        <ul>
     *          <li>Spring应用上下文</li>
     *          <li>工作流执行图实例</li>
     *          <li>事件总线引用</li>
     *          <li>工作流定义及实例</li>
     *          <li>任务定义及可能存在的任务实例</li>
     *        </ul>
     * @throws NullPointerException 当必要参数为null时抛出
     */
    public TaskExecutionRunnable(TaskExecutionRunnableBuilder taskExecutionRunnableBuilder) {
        // 参数校验（Guava Preconditions）
        this.applicationContext = taskExecutionRunnableBuilder.getApplicationContext();
        this.workflowExecutionGraph = checkNotNull(taskExecutionRunnableBuilder.getWorkflowExecutionGraph());
        this.workflowEventBus = checkNotNull(taskExecutionRunnableBuilder.getWorkflowEventBus());
        this.workflowDefinition = checkNotNull(taskExecutionRunnableBuilder.getWorkflowDefinition());
        this.project = checkNotNull(taskExecutionRunnableBuilder.getProject());
        this.workflowInstance = checkNotNull(taskExecutionRunnableBuilder.getWorkflowInstance());
        this.taskDefinition = checkNotNull(taskExecutionRunnableBuilder.getTaskDefinition());
        this.taskInstance = taskExecutionRunnableBuilder.getTaskInstance();

        // 若存在已有任务实例，初始化执行上下文
        if (isTaskInstanceInitialized()) {
            initializeTaskExecutionContext();
        }
    }

    /**
     * 校验任务实例是否已初始化
     *
     * @return true表示存在有效任务实例，可能场景：
     *         <ul>
     *           <li>从持久化存储恢复的任务</li>
     *           <li>故障转移后重建的实例</li>
     *           <li>已初始化但未执行的任务</li>
     *         </ul>
     */
    @Override
    public boolean isTaskInstanceInitialized() {
        return taskInstance != null;
    }

    /**
     * 初始化首运行任务实例
     * <p>
     * 前置条件：任务实例未初始化
     *
     * @throws IllegalStateException 当重复初始化时抛出
     * @see FirstRunTaskInstanceFactory 使用的首运行工厂
     */
    @Override
    public void initializeFirstRunTaskInstance() {
        checkState(!isTaskInstanceInitialized(),
                "The task instance is already initialized, can't initialize first run task.");
        // 通过工厂创建初始任务实例
        this.taskInstance = applicationContext.getBean(TaskInstanceFactories.class)
                .firstRunTaskInstanceFactory()
                .builder()
                .withTaskDefinition(taskDefinition)
                .withWorkflowInstance(workflowInstance)
                .build();
        initializeTaskExecutionContext();
    }

    /**
     * 判断任务是否可重试
     *
     * @return true需满足：
     *         <ul>
     *           <li>当前重试次数 < 最大允许次数</li>
     *           <li>任务处于可重试状态（如FAILURE）</li>
     *           <li>系统配置允许重试</li>
     *         </ul>
     */
    @Override
    public boolean isTaskInstanceCanRetry() {
        return taskInstance.getRetryTimes() < taskInstance.getMaxRetryTimes();
    }

    /**
     * 执行任务重试
     * <p>
     * 实现步骤：
     * <ol>
     *   <li>校验当前实例状态可重试</li>
     *   <li>通过RetryTaskInstanceFactory创建新实例</li>
     *   <li>保留原始任务的上下文信息</li>
     *   <li>更新重试计数器</li>
     *   <li>发布任务启动事件</li>
     * </ol>
     *
     * @throws IllegalStateException 当任务未初始化时抛出
     */
    @Override
    public void retry() {
        checkState(isTaskInstanceInitialized(), "The task instance is not initialized, can't initialize retry task.");
        this.taskInstance = applicationContext.getBean(TaskInstanceFactories.class)
                .retryTaskInstanceFactory()
                .builder()
                .withTaskInstance(taskInstance)
                .build();
        initializeTaskExecutionContext();
        getWorkflowEventBus().publish(TaskStartLifecycleEvent.of(this));
    }


    /**
     * 执行故障转移
     * <p>
     * 处理逻辑：
     * <ol>
     *   <li>尝试从执行器接管任务（takeOverTaskFromExecutor）</li>
     *   <li>若接管失败则创建故障转移实例</li>
     *   <li>保持原始重试次数</li>
     *   <li>重新分配Worker节点</li>
     *   <li>发布任务启动事件</li>
     * </ol>
     */
    @Override
    public void failover() {
        checkState(isTaskInstanceInitialized(), "The task instance is not initialized, can't failover.");
        if (takeOverTaskFromExecutor()) {
            log.info("Failover task success, the task {} has been taken-over from executor", taskInstance.getName());
            return;
        }
        this.taskInstance = applicationContext.getBean(TaskInstanceFactories.class)
                .failoverTaskInstanceFactory()
                .builder()
                .withTaskInstance(taskInstance)
                .build();
        initializeTaskExecutionContext();

        getWorkflowEventBus().publish(TaskStartLifecycleEvent.of(this));
    }


    /**
     * 暂停任务执行
     * <p>
     * 实现机制：
     * <ul>
     *   <li>发布TaskPauseLifecycleEvent事件</li>
     *   <li>由事件监听器执行具体暂停操作</li>
     *   <li>更新任务状态为PAUSED</li>
     * </ul>
     */
    @Override
    public void pause() {
        getWorkflowEventBus().publish(TaskPauseLifecycleEvent.of(this));
    }


    /**
     * 终止任务执行
     * <p>
     * 实现机制：
     * <ul>
     *   <li>发布TaskKillLifecycleEvent事件</li>
     *   <li>由事件监听器通知Worker终止进程</li>
     *   <li>更新任务状态为KILLED</li>
     *   <li>释放占用的资源配额</li>
     * </ul>
     */
    @Override
    public void kill() {
        getWorkflowEventBus().publish(TaskKillLifecycleEvent.of(this));
    }


    /**
     * 初始化任务执行上下文
     * <p>
     * 构建包含运行时信息的上下文对象：
     * <ul>
     *   <li>合并工作流级和任务级参数</li>
     *   <li>注入环境变量</li>
     *   <li>配置资源限制</li>
     * </ul>
     *
     * @throws IllegalStateException 当任务实例未初始化时抛出
     */
    private void initializeTaskExecutionContext() {
        checkState(isTaskInstanceInitialized(), "The task instance is null, can't initialize TaskExecutionContext.");
        final TaskExecutionContextCreateRequest request = TaskExecutionContextCreateRequest.builder()
                .workflowDefinition(workflowDefinition)
                .project(project)
                .workflowInstance(workflowInstance)
                .taskDefinition(taskDefinition)
                .taskInstance(taskInstance)
                .build();
        this.taskExecutionContext = applicationContext.getBean(TaskExecutionContextFactory.class)
                .createTaskExecutionContext(request);
    }

    /**
     * 从执行器接管任务
     * <p>
     * 用于故障转移场景，尝试保持原任务实例：
     * <ol>
     *   <li>通过ITaskExecutorClient重新分配主机</li>
     *   <li>保留当前任务实例ID</li>
     *   <li>避免创建新实例带来的开销</li>
     * </ol>
     *
     * @return true表示接管成功，false需创建新实例
     */
    private boolean takeOverTaskFromExecutor() {
        checkState(isTaskInstanceInitialized(), "The task instance is null, can't take over from executor.");
        try {
            return applicationContext.getBean(ITaskExecutorClient.class).reassignWorkflowInstanceHost(this);
        } catch (Exception ex) {
            log.warn("Take over task: {} failed", taskInstance.getName(), ex);
            return false;
        }
    }

    /**
     * 任务优先级比较逻辑
     * <p>
     * 排序规则（降序优先级）：
     * <ol>
     *   <li>工作流实例优先级（高→低）</li>
     *   <li>任务实例优先级（高→低）</li>
     *   <li>任务组优先级（大→小）</li>
     *   <li>首次提交时间（早→晚）</li>
     * </ol>
     *
     * @param other 比较对象
     * @return 正数表示当前对象优先级更高
     */
    @Override
    public int compareTo(ITaskExecutionRunnable other) {
        if (other == null) {
            return 1;
        }

        // 工作流实例优先级比较
        int workflowInstancePriorityCompareResult = workflowInstance.getWorkflowInstancePriority().getCode() -
                other.getWorkflowInstance().getWorkflowInstancePriority().getCode();
        if (workflowInstancePriorityCompareResult != 0) {
            return workflowInstancePriorityCompareResult;
        }

        // 任务实例优先级比较（数值小优先级高）
        int taskInstancePriorityCompareResult = taskInstance.getTaskInstancePriority().getCode()
                - other.getTaskInstance().getTaskInstancePriority().getCode();
        if (taskInstancePriorityCompareResult != 0) {
            return taskInstancePriorityCompareResult;
        }

        // 任务组优先级比较（数值大优先级高）
        int taskGroupPriorityCompareResult =
                taskInstance.getTaskGroupPriority() - other.getTaskInstance().getTaskGroupPriority();
        if (taskGroupPriorityCompareResult != 0) {
            return -taskGroupPriorityCompareResult;
        }

        // 提交时间比较（早提交优先）
        return taskInstance.getFirstSubmitTime().compareTo(other.getTaskInstance().getFirstSubmitTime());
    }

    /**
     * 对象描述信息
     * <p>
     * 格式示例：
     * <ul>
     *   <li>已初始化：TaskExecutionRunnable{name=数据分析任务, state=RUNNING}</li>
     *   <li>未初始化：TaskExecutionRunnable{name=数据清洗任务}</li>
     * </ul>
     */
    @Override
    public String toString() {
        if (taskInstance != null) {
            return "TaskExecutionRunnable{" + "name=" + getName() + ", state=" + taskInstance.getState() + '}';
        }
        return "TaskExecutionRunnable{" + "name=" + getName() + '}';
    }
}
