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

import lombok.extern.slf4j.Slf4j;
import org.apache.dolphinscheduler.plugin.task.api.parameters.AbstractParameters;
import org.apache.dolphinscheduler.spi.plugin.PrioritySPIFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * 任务插件管理器（SPI机制 + 单例模式）
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>插件自动发现</b>：通过SPI加载所有TaskChannelFactory实现</li>
 *   <li><b>插件生命周期管理</b>：保证插件只加载一次</li>
 *   <li><b>统一入口</b>：提供任务参数校验、任务通道获取等公共服务</li>
 * </ol>
 *
 * <p>设计特性：
 * <ul>
 *   <li>线程安全：通过AtomicBoolean保障初始化原子性</li>
 *   <li>插件热加载：支持运行时动态注册新插件（需重新加载）</li>
 *   <li>优先级支持：通过@PrioritySPI注解定义插件优先级</li>
 * </ul>
 */
@Slf4j
public class TaskPluginManager {

    /**
     * 任务插件注册表（注册中心模式）
     * <p>数据结构：HashMap<任务类型, 任务通道>
     */
    private static final Map<String, TaskChannel> taskChannelMap = new HashMap<>();

    /**
     * 插件加载状态标志（CAS原子操作保障）
     */
    private static final AtomicBoolean loadedFlag = new AtomicBoolean(false);

    /**
     * 初始化加载所有任务插件（SPI机制）
     */
    static {
        loadTaskPlugin();
    }

    /**
     * 加载任务插件核心逻辑（模板方法模式）
     * <p>执行流程：
     * 1. 通过SPI发现所有TaskChannelFactory实现
     * 2. 按优先级排序后创建TaskChannel实例
     * 3. 注册到taskChannelMap
     */
    public static void loadTaskPlugin() {
        if (!loadedFlag.compareAndSet(false, true)) {
            log.warn("任务插件已加载");
            return;
        }
        PrioritySPIFactory<TaskChannelFactory> prioritySPIFactory = new PrioritySPIFactory<>(TaskChannelFactory.class);
        for (Map.Entry<String, TaskChannelFactory> entry : prioritySPIFactory.getSPIMap().entrySet()) {
            String factoryName = entry.getKey();
            TaskChannelFactory factory = entry.getValue();

            taskChannelMap.put(factoryName, factory.create());
            log.info("成功注册任务插件: {}", factoryName);
        }

    }

    /**
     * 获取任务通道（工厂方法模式）
     * 按类型获取TaskChannel，如果找不到TaskChannel，将抛出
     *
     * @param type 任务类型，不能为空 任务类型（如SHELL、SQL等）
     * @throws IllegalArgumentException 如果找不到TaskChannel 当插件未注册时抛出
     */
    public static TaskChannel getTaskChannel(String type) {
        checkNotNull(type, "type cannot be null");
        TaskChannel taskChannel = taskChannelMap.get(type);
        if (taskChannel == null) {
            throw new IllegalArgumentException("Cannot find TaskChannel for : " + type);
        }
        return taskChannel;
    }

    /**
     * 检查任务参数是否已验证（门面模式）
     *
     * @param taskType 任务类型，不能为null
     * @param taskParams 任务参数
     * @return 如果任务参数已验证，则返回true，否则返回false 如果找不到TaskChannel，@抛出IllegalArgumentException
     * @throws IllegalArgumentException 如果无法反序列化任务参数，则@抛出IllegalArgumentException
     */
    public static boolean checkTaskParameters(String taskType, String taskParams) {
        AbstractParameters abstractParameters = parseTaskParameters(taskType, taskParams);
        return abstractParameters.checkParameters();
    }

    /**
     * 解析任务参数（桥接模式）
     * <p>桥接关系：TaskChannel ↔ AbstractParameters
     *
     * @param taskType   任务类型，不能为空
     * @param taskParams 任务参数
     * @return AbstractParameters 抽象参数
     * @throws IllegalArgumentException 如果找不到TaskChannel
     * @throws IllegalArgumentException 如果无法反序列化任务参数
     */
    public static AbstractParameters parseTaskParameters(String taskType, String taskParams) {
        checkNotNull(taskType, "taskType不能为空");
        TaskChannel taskChannel = getTaskChannel(taskType);
        AbstractParameters abstractParameters = taskChannel.parseParameters(taskParams);
        if (abstractParameters == null) {
            throw new IllegalArgumentException("无法解析任务参数: " + taskParams + " for : " + taskType);
        }
        return abstractParameters;
    }

}
