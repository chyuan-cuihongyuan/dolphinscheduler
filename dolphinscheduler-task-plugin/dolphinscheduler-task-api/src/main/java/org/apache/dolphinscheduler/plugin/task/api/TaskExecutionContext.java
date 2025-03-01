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

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.dolphinscheduler.plugin.task.api.enums.TaskTimeoutStrategy;
import org.apache.dolphinscheduler.plugin.task.api.model.Property;
import org.apache.dolphinscheduler.plugin.task.api.parameters.resource.ResourceParametersHelper;
import org.apache.dolphinscheduler.plugin.task.api.resource.ResourceContext;

import java.io.Serializable;
import java.util.Map;

/**
 * 任务执行上下文（数据传输对象）
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>环境信息聚合</b>：封装任务运行所需的全量上下文数据</li>
 *   <li><b>跨节点传输</b>：序列化后在工作流引擎与Worker节点间传递</li>
 *   <li><b>资源桥接</b>：连接任务参数与运行时资源（数据源/文件等）</li>
 * </ol>
 *
 * <p>设计要点：
 * <ul>
 *   <li>Builder模式构造复杂对象</li>
 *   <li>JSON序列化支持网络传输</li>
 *   <li>线程安全（字段均为基本类型/不可变对象）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskExecutionContext implements Serializable {

    private static final long serialVersionUID = -1L;

    private int taskInstanceId;

    private String taskName;

    private long firstSubmitTime;

    private long startTime;

    private String taskType;

    private String workflowInstanceHost;

    private String host;

    private String executePath;

    private String logPath;

    private String appInfoPath;

    private int processId;

    private Long workflowDefinitionCode;

    private int workflowDefinitionVersion;

    private String appIds;

    private int workflowInstanceId;

    private long scheduleTime;

    private String globalParams;

    private int executorId;

    private int cmdTypeIfComplement;

    private String tenantCode;

    private int workflowDefinitionId;

    private int projectId;

    private long projectCode;

    private String taskParams;

    private String environmentConfig;

    /**
     * definedParams
     * // todo: we need to rename definedParams, prepareParamsMap, paramsMap, this is confusing
     */
    private Map<String, String> definedParams;

    private Map<String, Property> prepareParamsMap;

    // Please use task instanceId
    @Deprecated
    private String taskAppId;

    private TaskTimeoutStrategy taskTimeoutStrategy;

    private int taskTimeout;

    private String workerGroup;

    private ResourceParametersHelper resourceParametersHelper;

    private long endTime;

    private SQLTaskExecutionContext sqlTaskExecutionContext;

    private K8sTaskExecutionContext k8sTaskExecutionContext;

    private ResourceContext resourceContext;

    private String varPool;

    private int dryRun;

    private Map<String, Property> paramsMap;

    private Integer cpuQuota;

    private Integer memoryMax;

    private int testFlag;

    private boolean logBufferEnable;

    private int dispatchFailTimes;

    private boolean failover;

    public int increaseDispatchFailTimes() {
        return ++dispatchFailTimes;
    }
}
