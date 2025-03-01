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

import org.apache.dolphinscheduler.spi.plugin.PrioritySPI;
import org.apache.dolphinscheduler.spi.plugin.SPIIdentify;

/**
 * 任务通道工厂接口（工厂方法模式 + SPI扩展机制）
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>插件标识定义</b>：通过getName()声明任务类型（如SHELL/SQL）</li>
 *   <li><b>通道实例创建</b>：生产具体TaskChannel实现</li>
 *   <li><b>优先级管理</b>：继承PrioritySPI支持插件权重</li>
 * </ol>
 *
 * <p>实现要求：
 * <ul>
 *   <li>每个插件模块必须提供实现类（通过SPI配置文件注册）</li>
 *   <li>任务类型名称必须全局唯一</li>
 * </ul>
 */
public interface TaskChannelFactory extends PrioritySPI {

    /**
     * 获取SPI标识信息（模板方法模式）
     * <p>默认实现：使用插件名称作为唯一标识
     */
    default SPIIdentify getIdentify() {
        return SPIIdentify.builder()
                .name(getName())
                .build();
    }

    /**
     * 获取任务类型标识（策略模式）
     * @return 任务类型字符串（如"SHELL", "SQL"）
     *
     * <p>命名规范：
     * <ul>
     *   <li>全大写字母</li>
     *   <li>与任务定义类型字段一致</li>
     * </ul>
     */
    String getName();

    /**
     * 创建任务通道实例（工厂方法模式）
     * @return 非空TaskChannel实现
     *
     * <p>实现要求：
     * <ul>
     *   <li>每次调用必须返回新实例或线程安全实例</li>
     *   <li>实现类应包含任务执行逻辑</li>
     * </ul>
     */
    TaskChannel create();

}
