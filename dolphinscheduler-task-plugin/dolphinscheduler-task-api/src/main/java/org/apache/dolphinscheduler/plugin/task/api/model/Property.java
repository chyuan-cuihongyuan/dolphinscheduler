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

package org.apache.dolphinscheduler.plugin.task.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.dolphinscheduler.plugin.task.api.enums.DataType;
import org.apache.dolphinscheduler.plugin.task.api.enums.Direct;

import java.io.Serializable;
import java.util.Objects;

/**
 * 任务参数属性模型（值对象模式）
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>参数定义</b>：封装任务参数的元数据</li>
 *   <li><b>数据流向控制</b>：通过direct字段区分输入/输出参数</li>
 *   <li><b>类型安全</b>：明确参数的数据类型（STRING/INT等）</li>
 * </ol>
 *
 * <p>使用场景：
 * <ul>
 *   <li>任务参数定义（localParams）</li>
 *   <li>工作流变量池（varPool）</li>
 *   <li>跨任务参数传递</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Property implements Serializable {

    /**
     * 序列化版本标识（集群环境下跨节点传输）
     */
    private static final long serialVersionUID = -4045513703397452451L;

    /**
     * 参数唯一标识（命名规范：驼峰格式，全局唯一）
     * <p>示例：'sourceTableName'
     */
    private String prop;

    /**
     * 参数流向（输入/输出）
     * @see Direct
     */
    private Direct direct;

    /**
     * 数据类型（支持类型见DataType枚举）
     * @see DataType
     */
    private DataType type;

    /**
     * 参数值（支持表达式替换）
     * <p>示例：'${system.bizdate}' → 实际运⾏时替换为具体日期
     */
    private String value;

    /**
     * 相等性判断（业务键：prop + value）
     * <p>设计说明：相同参数名+参数值视为相等，与direct/type无关
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Property property = (Property) o;
        return Objects.equals(prop, property.prop)
                && Objects.equals(value, property.value);
    }

    /**
     * 哈希值计算（与equals()保持一致性）
     */
    @Override
    public int hashCode() {
        return Objects.hash(prop, value);
    }

    /**
     * 完整属性输出（调试用）
     * <p>输出示例：Property{prop='taskId', direct=IN, type=INT, value='1001'}
     */
    @Override
    public String toString() {
        return "Property{"
                + "prop='" + prop + '\''
                + ", direct=" + direct
                + ", type=" + type
                + ", value='" + value + '\''
                + '}';
    }

}
