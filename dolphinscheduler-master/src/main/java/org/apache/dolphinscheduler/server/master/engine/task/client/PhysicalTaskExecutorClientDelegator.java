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

package org.apache.dolphinscheduler.server.master.engine.task.client;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dolphinscheduler.dao.entity.TaskInstance;
import org.apache.dolphinscheduler.extract.base.client.Clients;
import org.apache.dolphinscheduler.extract.base.utils.Host;
import org.apache.dolphinscheduler.extract.worker.IPhysicalTaskExecutorOperator;
import org.apache.dolphinscheduler.plugin.task.api.TaskExecutionContext;
import org.apache.dolphinscheduler.server.master.cluster.loadbalancer.IWorkerLoadBalancer;
import org.apache.dolphinscheduler.server.master.config.MasterConfig;
import org.apache.dolphinscheduler.server.master.engine.task.runnable.ITaskExecutionRunnable;
import org.apache.dolphinscheduler.server.master.exception.dispatch.TaskDispatchException;
import org.apache.dolphinscheduler.task.executor.eventbus.ITaskExecutorLifecycleEventReporter;
import org.apache.dolphinscheduler.task.executor.operations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * 物理任务执行器客户端委托器
 *
 * 核心职责：
 * 1. 作为Master与Worker节点间的操作代理网关
 * 2. 实现任务全生命周期操作（分发、暂停、终止等）
 * 3. 集成负载均衡策略选择目标Worker节点
 * 4. 处理与Worker节点的RPC通信及响应
 *
 * 设计模式：
 * - 委托模式：将具体操作委托给Worker节点执行
 * - 门面模式：封装复杂的分布式操作流程
 */
@Slf4j
@Component
public class PhysicalTaskExecutorClientDelegator implements ITaskExecutorClientDelegator {

    // 主节点配置（含地址等信息）
    @Autowired
    private MasterConfig masterConfig;

    // 负载均衡器（策略可配置）
    @Autowired
    private IWorkerLoadBalancer workerLoadBalancer;

    /**
     * 任务分发核心逻辑
     *
     * 执行流程：
     * 1. 使用负载均衡器选择Worker节点
     * 2. 设置任务上下文中的主机信息
     * 3. 通过RPC调用Worker节点分发任务
     * 4. 处理分发结果
     *
     * 异常处理策略：
     * - 节点选择失败：立即抛出TaskDispatchException
     * - RPC调用异常：包装为TaskDispatchException
     * - Worker处理失败：透传错误信息
     */
    @Override
    public void dispatch(final ITaskExecutionRunnable taskExecutionRunnable) throws TaskDispatchException {
        final TaskExecutionContext taskExecutionContext = taskExecutionRunnable.getTaskExecutionContext();
        final String taskName = taskExecutionContext.getTaskName();

        // 负载均衡选择节点（可能阻塞）
        final String physicalTaskExecutorAddress = workerLoadBalancer
                .select(taskExecutionContext.getWorkerGroup())
                .map(Host::of)
                .map(Host::getAddress)
                .orElseThrow(() -> new TaskDispatchException(
                        String.format("找不到要分派任务的主机[id=%s, name=%s, workerGroup=%s]",
                                taskExecutionContext.getTaskInstanceId(), taskName,
                                taskExecutionContext.getWorkerGroup())));

        // 设置任务实例主机信息
        taskExecutionContext.setHost(physicalTaskExecutorAddress);
        taskExecutionRunnable.getTaskInstance().setHost(physicalTaskExecutorAddress);

        try {
            // 构建RPC客户端并发送请求
            final TaskExecutorDispatchResponse taskExecutorDispatchResponse = Clients
                    .withService(IPhysicalTaskExecutorOperator.class)
                    .withHost(physicalTaskExecutorAddress)
                    .dispatchTask(TaskExecutorDispatchRequest.of(taskExecutionRunnable.getTaskExecutionContext()));

            // 处理Worker节点响应
            if (!taskExecutorDispatchResponse.isDispatchSuccess()) {
                throw new TaskDispatchException(
                        "Dispatch task: " + taskName + " to " + physicalTaskExecutorAddress + " failed: "
                                + taskExecutorDispatchResponse);
            }
        } catch (TaskDispatchException e) {
            throw e;// 直接抛出已知异常
        } catch (Exception e) {
            throw new TaskDispatchException(
                    "Dispatch task: " + taskName + " to " + physicalTaskExecutorAddress + " failed", e);
        }
    }


    /**
     * 主节点重新分配（故障转移场景）
     *
     * 典型场景：
     * - Master节点故障恢复后重新接管任务
     * - 集群脑裂后的状态修复
     *
     * 执行保障：
     * 1. 任务必须已初始化（checkArgument校验）
     * 2. 记录详细的接管日志
     * 3. 同步等待Worker节点确认
     */
    @Override
    public boolean reassignMasterHost(final ITaskExecutionRunnable taskExecutionRunnable) {
        final String taskName = taskExecutionRunnable.getName();
        // 前置校验：任务必须已初始化
        checkArgument(taskExecutionRunnable.isTaskInstanceInitialized(),
                "Task " + taskName + "is not initialized cannot take-over");

        final TaskInstance taskInstance = taskExecutionRunnable.getTaskInstance();
        final String taskExecutorHost = taskInstance.getHost();

        // 空主机地址处理
        if (StringUtils.isEmpty(taskExecutorHost)) {
            log.debug(
                    "The task executor: {} host is empty, cannot take-over, this might caused by the task hasn't dispatched", //任务执行器：｛｝主机为空，无法接管，这可能是由于任务尚未分派造成的
                    taskName);
            return false;
        }

        // 构建主节点接管请求
        final TaskExecutorReassignMasterRequest taskExecutorReassignMasterRequest =
                TaskExecutorReassignMasterRequest.builder()
                        .taskInstanceId(taskInstance.getId())
                        .workflowHost(masterConfig.getMasterAddress())
                        .build();

        // 发送接管请求
        final TaskExecutorReassignMasterResponse taskExecutorReassignMasterResponse =
                Clients
                        .withService(IPhysicalTaskExecutorOperator.class)
                        .withHost(taskInstance.getHost())
                        .reassignWorkflowInstanceHost(taskExecutorReassignMasterRequest);

        // 处理响应结果
        boolean success = taskExecutorReassignMasterResponse.isSuccess();
        if (success) {
            log.info("Reassign master host {} to {} successfully", taskExecutorHost, taskName);
        } else {
            log.info("Reassign master host {} on {} failed with response {}",
                    taskExecutorHost,
                    taskName,
                    taskExecutorReassignMasterResponse);
        }
        return success;
    }

    /**
     * 任务暂停操作
     *
     * 安全机制：
     * - 前置校验确保主机地址非空
     * - 同步等待操作确认
     * - 详细记录操作结果
     */
    @Override
    public void pause(final ITaskExecutionRunnable taskExecutionRunnable) {
        final TaskInstance taskInstance = taskExecutionRunnable.getTaskInstance();
        final String executorHost = taskInstance.getHost();
        final String taskName = taskInstance.getName();

        // 地址校验
        checkArgument(StringUtils.isNotEmpty(executorHost), "Executor host is empty");

        // 发送暂停请求
        final TaskExecutorPauseResponse pauseResponse = Clients
                .withService(IPhysicalTaskExecutorOperator.class)
                .withHost(taskInstance.getHost())
                .pauseTask(TaskExecutorPauseRequest.of(taskInstance.getId()));

        // 处理响应
        if (pauseResponse.isSuccess()) {
            log.info("Pause task {} on executor {} successfully", taskName, executorHost);
        } else {
            log.warn("Pause task {} on executor {} failed with response {}", taskName, executorHost, pauseResponse);
        }
    }

    /**
     * 任务终止操作
     *
     * 增强特性：
     * - 强制终止机制（需Worker端实现）
     * - 支持异步终止确认
     */
    @Override
    public void kill(final ITaskExecutionRunnable taskExecutionRunnable) {
        final TaskInstance taskInstance = taskExecutionRunnable.getTaskInstance();
        final String executorHost = taskInstance.getHost();
        final String taskName = taskInstance.getName();
        checkArgument(StringUtils.isNotEmpty(executorHost), "Executor host is empty");

        // 发送终止请求
        final TaskExecutorKillResponse killResponse = Clients
                .withService(IPhysicalTaskExecutorOperator.class)
                .withHost(executorHost)
                .killTask(TaskExecutorKillRequest.of(taskInstance.getId()));
        // 处理响应
        if (killResponse.isSuccess()) {
            log.info("Kill task {} on executor {} successfully", taskName, executorHost);
        } else {
            log.warn("Kill task {} on executor {} failed with response {}", taskName, executorHost, killResponse);
        }
    }

    /**
     * 生命周期事件确认
     *
     * 注意事项：
     * - 异步确认机制
     * - 无返回值处理（需确保Worker端可靠性）
     * - 用于任务状态同步
     */
    @Override
    public void ackTaskExecutorLifecycleEvent(final ITaskExecutionRunnable taskExecutionRunnable,
                                              final ITaskExecutorLifecycleEventReporter.TaskExecutorLifecycleEventAck taskExecutorLifecycleEventAck) {
        final TaskInstance taskInstance = taskExecutionRunnable.getTaskInstance();
        final String executorHost = taskInstance.getHost();
        checkArgument(StringUtils.isNotEmpty(executorHost), "Executor host is empty");

        // 发送异步确认（不等待响应）
        Clients
                .withService(IPhysicalTaskExecutorOperator.class)
                .withHost(executorHost)
                .ackPhysicalTaskExecutorLifecycleEvent(taskExecutorLifecycleEventAck);
    }

}
