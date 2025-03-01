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

package org.apache.dolphinscheduler.plugin.task.api.parameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.plugin.task.api.K8sTaskExecutionContext;
import org.apache.dolphinscheduler.plugin.task.api.enums.Direct;
import org.apache.dolphinscheduler.plugin.task.api.enums.ResourceType;
import org.apache.dolphinscheduler.plugin.task.api.model.Property;
import org.apache.dolphinscheduler.plugin.task.api.model.ResourceInfo;
import org.apache.dolphinscheduler.plugin.task.api.parameters.resource.DataSourceParameters;
import org.apache.dolphinscheduler.plugin.task.api.parameters.resource.ResourceParametersHelper;
import org.apache.dolphinscheduler.plugin.task.api.utils.VarPoolUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务参数抽象基类（模板方法模式）
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>参数校验规范</b>：定义checkParameters()校验模板</li>
 *   <li><b>参数转换管理</b>：处理本地参数与变量池的交互</li>
 *   <li><b>资源上下文生成</b>：创建K8s等运行时环境配置</li>
 * </ol>
 *
 * <p>字段说明：
 * <ul>
 *   <li>localParams：任务本地参数（输入/输出）</li>
 *   <li>varPool：工作流级变量池（跨任务传递）</li>
 * </ul>
 */
@Getter
@Slf4j
public abstract class AbstractParameters implements IParameters {

    /**
     * 任务本地参数（作用域：当前任务）
     * <p>包含输入(IN)和输出(OUT)两种类型
     */
    @Setter
    public List<Property> localParams;

    /**
     * 工作流变量池（作用域：整个工作流）
     * <p>存储格式：List<Property>（自动去重）
     */
    public List<Property> varPool = new ArrayList<>();

    /**
     * 参数校验模板方法（必须实现）
     * <p>校验规则示例：
     * <ul>
     *   <li>必填参数检查</li>
     *   <li>路径合法性校验</li>
     *   <li>参数逻辑验证</li>
     * </ul>
     */
    @Override
    public abstract boolean checkParameters();

    @Override
    public List<ResourceInfo> getResourceFilesList() {
        return new ArrayList<>();
    }

    public Map<String, Property> getLocalParametersMap() {
        Map<String, Property> localParametersMaps = new LinkedHashMap<>();
        if (localParams != null) {
            for (Property property : localParams) {
                localParametersMaps.put(property.getProp(), property);
            }
        }
        return localParametersMaps;
    }

    /**
     * 生成K8s任务执行上下文（工厂方法）
     * @param parametersHelper 资源参数帮助类
     * @param datasource 数据源ID
     * @return 包含连接参数的K8s上下文
     */
    public K8sTaskExecutionContext generateK8sTaskExecutionContext(ResourceParametersHelper parametersHelper,
                                                                   int datasource) {
        DataSourceParameters dataSourceParameters =
                (DataSourceParameters) parametersHelper.getResourceParameters(ResourceType.DATASOURCE, datasource);
        K8sTaskExecutionContext k8sTaskExecutionContext = new K8sTaskExecutionContext();
        k8sTaskExecutionContext.setConnectionParams(
                Objects.nonNull(dataSourceParameters) ? dataSourceParameters.getConnectionParams() : null);
        return k8sTaskExecutionContext;
    }

    /**
     * get input local parameters map if the param direct is IN
     *
     * @return parameters map
     */
    public Map<String, Property> getInputLocalParametersMap() {
        Map<String, Property> localParametersMaps = new LinkedHashMap<>();
        if (localParams != null) {
            for (Property property : localParams) {
                // The direct of some tasks is empty, default IN
                if (property.getDirect() == null || Objects.equals(Direct.IN, property.getDirect())) {
                    localParametersMaps.put(property.getProp(), property);
                }
            }
        }
        return localParametersMaps;
    }

    /**
     * get varPool map
     *
     * @return parameters map
     */
    public Map<String, Property> getVarPoolMap() {
        Map<String, Property> varPoolMap = new LinkedHashMap<>();
        if (varPool != null) {
            for (Property property : varPool) {
                varPoolMap.put(property.getProp(), property);
            }
        }
        return varPoolMap;
    }

    public void setVarPool(String varPool) {
        if (StringUtils.isEmpty(varPool)) {
            this.varPool = new ArrayList<>();
        } else {
            this.varPool = JSONUtils.toList(varPool, Property.class);
        }
    }

    /**
     * 处理输出参数（观察者模式）
     * <p>将任务输出参数注入变量池，供下游任务使用
     *
     * @param taskOutputParams 任务输出参数（key: 参数名, value: 参数值）
     */
    public void dealOutParam(Map<String, String> taskOutputParams) {
        List<Property> outProperty = getOutProperty(localParams);
        if (CollectionUtils.isEmpty(outProperty)) {
            return;
        }
        if (CollectionUtils.isNotEmpty(outProperty) && MapUtils.isNotEmpty(taskOutputParams)) {
            // Inject the value
            for (Property info : outProperty) {
                String value = taskOutputParams.get(info.getProp());
                if (value != null) {
                    info.setValue(value);
                }
            }
        }

        varPool = VarPoolUtils.mergeVarPool(Lists.newArrayList(varPool, outProperty));
    }

    protected List<Property> getOutProperty(List<Property> params) {
        if (CollectionUtils.isEmpty(params)) {
            return new ArrayList<>();
        }
        return params.stream()
                .filter(info -> info.getDirect() == Direct.OUT)
                .collect(Collectors.toList());
    }

    public List<Map<String, String>> getListMapByString(String json) {
        List<Map<String, String>> allParams = new ArrayList<>();
        ArrayNode paramsByJson = JSONUtils.parseArray(json);
        for (JsonNode jsonNode : paramsByJson) {
            Map<String, String> param = JSONUtils.toMap(jsonNode.toString());
            allParams.add(param);
        }
        return allParams;
    }

    public ResourceParametersHelper getResources() {
        return new ResourceParametersHelper();
    }

    /**
     * 添加参数到变量池（原子操作）
     * <p>实现特性：
     * <ul>
     *   <li>同名参数自动覆盖</li>
     *   <li>保持插入顺序</li>
     * </ul>
     */
    public void addPropertyToValPool(Property property) {
        varPool.removeIf(p -> p.getProp().equals(property.getProp()));
        varPool.add(property);
    }
}
