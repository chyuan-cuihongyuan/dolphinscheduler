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

package org.apache.dolphinscheduler.plugin.task.api.parameters.resource;

import org.apache.dolphinscheduler.plugin.task.api.enums.ResourceType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 资源参数管理助手（门面模式）
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>资源分类存储</b>：按资源类型建立二级映射</li>
 *   <li><b>参数快速检索</b>：支持类型+ID双重维度查询</li>
 *   <li><b>空值安全处理</b>：允许参数值为null的存储</li>
 * </ol>
 *
 * <p>数据结构：
 * Map<ResourceType, Map<资源ID, 资源参数对象>>
 */
public class ResourceParametersHelper {

    /**
     * 资源参数注册表（双重映射结构）
     * <p>典型存储内容：
     * <ul>
     *   <li>DATASOURCE → {1: MySQLParameters, 2: OracleParameters}</li>
     *   <li>FILE → {101: FileParameters}</li>
     * </ul>
     */
    private Map<ResourceType, Map<Integer, AbstractResourceParameters>> resourceMap = new HashMap<>();

    /**
     * 添加资源参数（快捷方式，参数对象为null）
     * @param resourceType 资源类型（如DATASOURCE）
     * @param id 资源唯一标识
     */
    public void put(ResourceType resourceType, Integer id) {
        put(resourceType, id, null);
    }

    /**
     * 添加资源参数（完整方式）
     * @param parameters 具体资源参数（可为null）
     */
    public void put(ResourceType resourceType, Integer id, AbstractResourceParameters parameters) {
        Map<Integer, AbstractResourceParameters> resourceParametersMap = resourceMap.get(resourceType);
        if (Objects.isNull(resourceParametersMap)) {
            resourceParametersMap = new HashMap<>();
            resourceMap.put(resourceType, resourceParametersMap);
        }
        resourceParametersMap.put(id, parameters);
    }

    public void setResourceMap(Map<ResourceType, Map<Integer, AbstractResourceParameters>> resourceMap) {
        this.resourceMap = resourceMap;
    }

    public Map<ResourceType, Map<Integer, AbstractResourceParameters>> getResourceMap() {
        return resourceMap;
    }

    /**
     * 获取指定类型的资源映射（可能返回null）
     * @param resourceType 资源类型过滤器
     * @return 该类型下所有资源的ID-参数映射
     */
    public Map<Integer, AbstractResourceParameters> getResourceMap(ResourceType resourceType) {
        return this.getResourceMap().get(resourceType);
    }

    /**
     * 精确获取资源参数（可能返回null）
     * @param code 资源唯一标识（对应添加时的id参数）
     */
    public AbstractResourceParameters getResourceParameters(ResourceType resourceType, Integer code) {
        return this.getResourceMap(resourceType).get(code);
    }
}
