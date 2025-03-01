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

package org.apache.dolphinscheduler.plugin.task.api;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.dolphinscheduler.plugin.task.api.enums.TaskExecutionStatus;
import org.apache.dolphinscheduler.plugin.task.api.model.Property;
import org.apache.dolphinscheduler.plugin.task.api.model.TaskAlertInfo;
import org.apache.dolphinscheduler.plugin.task.api.parameters.AbstractParameters;

import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;

/**
 * 任务抽象基类（模板方法模式）
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>执行流程控制</b>：定义任务处理的标准流程</li>
 *   <li><b>状态跟踪</b>：管理任务执行状态码和结果</li>
 *   <li><b>资源管理</b>：跟踪进程ID和资源管理器ID（如YARN应用ID）</li>
 * </ol>
 *
 * <p>生命周期方法：
 * <ul>
 *   <li>init()：初始化任务（可重写）</li>
 *   <li>handle()：执行核心逻辑（必须实现）</li>
 *   <li>cancel()：终止任务（必须实现）</li>
 * </ul>
 */
@Slf4j
public abstract class AbstractTask {

    /**
     * 任务输出参数（工作流上下文传递）
     * <p>格式：Map<参数名, 参数值>
     */
    @Getter
    @Setter
    protected Map<String, String> taskOutputParams;

    /**
     * 任务执行上下文（包含环境信息）
     * <p>包含：
     * <ul>
     *   <li>任务实例ID</li>
     *   <li>工作流实例信息</li>
     *   <li>资源文件列表</li>
     * </ul>
     */
    protected TaskExecutionContext taskRequest;

    /**
     * 本地进程ID（仅本地执行模式有效）
     */
    protected int processId;

    /**
     * 资源管理器应用ID（如YARN ApplicationID）
     */
    protected String appIds;

    /**
     * 任务退出状态码（原子可见性）
     * <p>取值规范：
     * <ul>
     *   <li>0：成功</li>
     *   <li>-1：默认初始值</li>
     *   <li>其他：失败具体原因</li>
     * </ul>
     */
    protected volatile int exitStatusCode = -1;

    /**
     * 是否需要发送告警
     */
    protected boolean needAlert = false;

    /**
     * 任务告警信息
     */
    protected TaskAlertInfo taskAlertInfo;

    /**
     * 构造函数，初始化任务执行上下文
     *
     * @param taskExecutionContext 任务执行上下文对象，包含任务实例ID、工作流实例信息、
     *                             资源文件列表等环境信息
     */
    protected AbstractTask(TaskExecutionContext taskExecutionContext) {
        this.taskRequest = taskExecutionContext;
    }

    /**
     * 初始化任务方法（空实现）
     * <p>子类可根据需要重写此方法，用于执行自定义初始化逻辑
     */
    public void init() {
    }

    /**
     * 执行结果处理（模板方法）
     * @param taskCallBack 回调接口，用于：
     * <ul>
     *   <li>上报任务状态</li>
     *   <li>传递输出参数</li>
     * </ul>
     */
    public abstract void handle(TaskCallBack taskCallBack) throws TaskException;

    /**
     * 终止任务抽象方法
     *
     * @throws TaskException 当任务终止操作执行失败时抛出
     */
    public abstract void cancel() throws TaskException;

    /**
     * get exit status code
     *
     * @return exit status code
     */
    public int getExitStatusCode() {
        return exitStatusCode;
    }


    public void setExitStatusCode(int exitStatusCode) {
        this.exitStatusCode = exitStatusCode;
    }

    public int getProcessId() {
        return processId;
    }

    public void setProcessId(int processId) {
        this.processId = processId;
    }

    public String getAppIds() {
        return appIds;
    }

    public void setAppIds(String appIds) {
        this.appIds = appIds;
    }

    public boolean getNeedAlert() {
        return needAlert;
    }

    public void setNeedAlert(boolean needAlert) {
        this.needAlert = needAlert;
    }

    public TaskAlertInfo getTaskAlertInfo() {
        return taskAlertInfo;
    }

    public void setTaskAlertInfo(TaskAlertInfo taskAlertInfo) {
        this.taskAlertInfo = taskAlertInfo;
    }

    /**
     * get task parameters
     *
     * @return AbstractParameters
     */
    public abstract AbstractParameters getParameters();

    /**
     * 获取任务退出状态（状态模式实现）
     * @return 任务执行最终状态：
     * <ul>
     *   <li>SUCCESS：exitStatusCode == 0</li>
     *   <li>KILL：主动终止</li>
     *   <li>FAILURE：其他非零状态码</li>
     * </ul>
     */
    public TaskExecutionStatus getExitStatus() {
        if (exitStatusCode == TaskConstants.EXIT_CODE_SUCCESS) {
            return TaskExecutionStatus.SUCCESS;
        }
        if (exitStatusCode == TaskConstants.EXIT_CODE_KILL || exitStatusCode == TaskConstants.EXIT_CODE_HARD_KILL) {
            return TaskExecutionStatus.KILL;
        }
        return TaskExecutionStatus.FAILURE;
    }

    /**
     * 日志聚合处理（观察者模式）
     * @param logs 日志队列，来源包括：
     * <ul>
     *   <li>标准输出</li>
     *   <li>错误输出</li>
     *   <li>自定义日志</li>
     * </ul>
     */
    public void logHandle(LinkedBlockingQueue<String> logs) {

        StringJoiner joiner = new StringJoiner("\n\t");
        while (!logs.isEmpty()) {
            joiner.add(logs.poll());
        }
        log.info(" -> {}", joiner);
    }

    /**
     * SQL参数替换（策略模式）
     * @param content SQL模板内容（含${param}占位符）
     * @param sqlParamsMap 有序参数映射（index→Property）
     * @param paramsPropsMap 原始参数池（name→Property）
     *
     * <p>替换逻辑：
     * 1. 使用正则匹配占位符
     * 2. 从paramsPropsMap获取实际值
     * 3. 按出现顺序存入sqlParamsMap
     */
    public void setSqlParamsMap(String content, Map<Integer, Property> sqlParamsMap,
                                Map<String, Property> paramsPropsMap, int taskInstanceId) {
        if (paramsPropsMap == null) {
            return;
        }

        Matcher m = TaskConstants.SQL_PARAMS_PATTERN.matcher(content);
        int index = 1;
        while (m.find()) {

            String paramName = m.group(TaskConstants.GROUP_NAME1);
            if (paramName == null) {
                paramName = m.group(TaskConstants.GROUP_NAME2);
            }

            Property prop = paramsPropsMap.get(paramName);

            if (prop == null) {
                log.error(
                        "setSqlParamsMap:没有带paramName的属性: {} 在任务实例的paramsPropsMap中找到"
                                + " 带有id: {}. 因此，无法将属性放入sqlParamsMap中.",
                        paramName, taskInstanceId);
            } else {
                sqlParamsMap.put(index, prop);
                index++;
                log.info(
                        "setSqlParamsMap:带有paramName的属性: {}放入sqlParamsMap内容 {} 成功地.",
                        paramName, content);
            }

        }
    }
}
