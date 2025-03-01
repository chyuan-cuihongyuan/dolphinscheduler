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

package org.apache.dolphinscheduler.server.master.engine;

import lombok.extern.slf4j.Slf4j;
import org.apache.dolphinscheduler.registry.api.Registry;
import org.apache.dolphinscheduler.registry.api.enums.RegistryNodeType;
import org.apache.dolphinscheduler.registry.api.ha.AbstractHAServer;
import org.apache.dolphinscheduler.registry.api.ha.AbstractServerStatusChangeListener;
import org.apache.dolphinscheduler.server.master.config.MasterConfig;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * 主节点协调器（单例模式 + HA扩展）
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>高可用管理</b>：基于父类实现主备切换</li>
 *   <li><b>资源协调</b>：统一管理任务组协调器</li>
 *   <li><b>生命周期管理</b>：确保服务启停时资源正确释放</li>
 * </ol>
 *
 * <p>关键特性：
 * <ul>
 *   <li>继承AbstractHAServer获得选主能力</li>
 *   <li>内置状态监听器联动任务组协调器</li>
 *   <li>Spring容器单例管理</li>
 * </ul>
 */
@Slf4j
@Component
public class MasterCoordinator extends AbstractHAServer {

    /**
     * 任务组协调器（集群任务调度核心）
     */
    private final ITaskGroupCoordinator taskGroupCoordinator;

    /**
     * 构造器（依赖注入）
     * @param registry 注册中心实例
     * @param masterConfig 主节点配置（获取服务地址）
     * @param taskGroupCoordinator 任务组协调器实例
     */
    public MasterCoordinator(final Registry registry,
                             final MasterConfig masterConfig,
                             final ITaskGroupCoordinator taskGroupCoordinator) {
        super(
                registry,
                RegistryNodeType.MASTER_COORDINATOR.getRegistryPath(),
                masterConfig.getMasterAddress());
        this.taskGroupCoordinator = taskGroupCoordinator;
        addServerStatusChangeListener(new MasterCoordinatorListener(taskGroupCoordinator));
    }

    /**
     * 启动协调器（扩展父类行为）
     * <p>执行顺序：
     * 1. 调用父类启动选主流程
     * 2. 记录启动日志
     */
    @Override
    public void start() {
        super.start();
        log.info("MasterCoordinator started...");
    }

    /**
     * 关闭协调器（资源清理）
     * <p>安全操作：
     * 1. 停止任务组协调器
     * 2. 记录停机日志
     */
    @Override
    public void close() {
        taskGroupCoordinator.close();
        log.info("MasterCoordinator shutdown...");
    }

    /**
     * 主节点协调器（高可用实现）
     *
     * <p>核心职责：
     * <ol>
     *   <li><b>HA状态管理</b>：继承AbstractHAServer实现主备切换</li>
     *   <li><b>任务组协调</b>：管理ITaskGroupCoordinator生命周期</li>
     *   <li><b>状态联动</b>：通过监听器同步协调器状态</li>
     * </ol>
     *
     * <p>配置说明：
     * <ul>
     *   <li>注册路径：RegistryNodeType.MASTER_COORDINATOR</li>
     *   <li>服务标识：masterConfig.masterAddress</li>
     * </ul>
     */
    public static class MasterCoordinatorListener extends AbstractServerStatusChangeListener {

        /**
         * 任务组协调器（负责资源分配和调度）
         */
        private final ITaskGroupCoordinator taskGroupCoordinator;

        /**
         * 主备状态监听器（内部类实现）
         *
         * <p>状态同步逻辑：
         * | 主节点状态 | 任务协调器状态 |
         * |------------|----------------|
         * | ACTIVE     | 启动           |
         * | STAND_BY   | 关闭           |
         */
        public MasterCoordinatorListener(ITaskGroupCoordinator taskGroupCoordinator) {
            this.taskGroupCoordinator = checkNotNull(taskGroupCoordinator);
        }

        // 当成为主节点时
        @Override
        public void changeToActive() {
            taskGroupCoordinator.start();
        }

        // 当降级为备节点时
        @Override
        public void changeToStandBy() {
            taskGroupCoordinator.close();
        }
    }

}
