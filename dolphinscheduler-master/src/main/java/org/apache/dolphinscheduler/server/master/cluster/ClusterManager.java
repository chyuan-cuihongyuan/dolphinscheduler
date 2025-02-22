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

package org.apache.dolphinscheduler.server.master.cluster;

import org.apache.dolphinscheduler.common.model.MasterHeartBeat;
import org.apache.dolphinscheduler.common.model.WorkerHeartBeat;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.registry.api.RegistryClient;
import org.apache.dolphinscheduler.registry.api.enums.RegistryNodeType;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 集群管理核心组件
 * <p>负责管理 Master/Worker 节点集群信息，功能包括：
 * <ul>
 *   <li>集群节点注册发现</li>
 *   <li>节点心跳信息维护</li>
 *   <li>集群变化事件订阅</li>
 *   <li>槽位分配协调</li>
 * </ul>
 */
@Slf4j
@Component
public class ClusterManager {

    /**
     * Master 节点集群信息存储
     */
    @Getter
    private MasterClusters masterClusters;

    /**
     * Worker 节点集群信息存储
     */
    @Getter
    private WorkerClusters workerClusters;

    /**
     * 主节点槽位管理器
     */
    @Autowired
    private MasterSlotManager masterSlotManager;

    /**
     * 工作节点组变更通知器
     */
    @Autowired
    private WorkerGroupChangeNotifier workerGroupChangeNotifier;

    /**
     * 注册中心客户端
     */
    @Autowired
    private RegistryClient registryClient;

    public ClusterManager() {
        this.masterClusters = new MasterClusters();
        this.workerClusters = new WorkerClusters();
    }

    /**
     * 启动集群管理服务
     * <p>执行顺序：
     * <ol>
     *   <li>初始化 Master 集群</li>
     *   <li>初始化 Worker 集群</li>
     * </ol>
     */
    public void start() {
        initializeMasterClusters();
        initializeWorkerClusters();
        log.info("ClusterManager started...");
    }

    /**
     * Initialize the master clusters.
     * <p> 1. Register master slot listener once master clusters changed.
     * <p> 2. Fetch master nodes from registry.
     * <p> 3. Subscribe the master change event.
     * 初始化主集群。
     * <p>1.主集群更改后，注册主插槽侦听器。
     * <p>2.从注册表中获取主节点。
     * <p>3.订阅主变更事件。
     */
    private void initializeMasterClusters() {
        this.masterClusters.registerListener(new MasterSlotChangeListenerAdaptor(masterSlotManager, masterClusters));

        registryClient.getServerList(RegistryNodeType.MASTER).forEach(server -> {
            final MasterHeartBeat masterHeartBeat =
                    JSONUtils.parseObject(server.getHeartBeatInfo(), MasterHeartBeat.class);
            masterClusters.onServerAdded(MasterServerMetadata.parseFromHeartBeat(masterHeartBeat));
        });
        log.info("已初始化WorkerClusters: {}", JSONUtils.toPrettyJsonString(masterClusters.getServers()));

        this.registryClient.subscribe(RegistryNodeType.MASTER.getRegistryPath(), masterClusters);
    }

    /**
     * Initialize the worker clusters.
     * <p> 1. Fetch worker nodes from registry.
     * <p> 2. Register worker group change notifier once worker clusters changed.
     * <p> 3. Subscribe the worker change event.
     * 初始化工作集群。
     * <p> 1.从注册表中获取工作节点。
     * <p> 2.一旦工人集群发生变化，就注册工人组更改通知程序。
     * <p> 3.订阅员工变更事件。
     */
    private void initializeWorkerClusters() {
        registryClient.getServerList(RegistryNodeType.WORKER).forEach(server -> {
            final WorkerHeartBeat workerHeartBeat =
                    JSONUtils.parseObject(server.getHeartBeatInfo(), WorkerHeartBeat.class);
            workerClusters.onServerAdded(WorkerServerMetadata.parseFromHeartBeat(workerHeartBeat));
        });
        log.info("已初始化WorkerClusters: {}", JSONUtils.toPrettyJsonString(workerClusters.getServers()));

        this.registryClient.subscribe(RegistryNodeType.WORKER.getRegistryPath(), workerClusters);

        this.workerGroupChangeNotifier.subscribeWorkerGroupsChange(workerClusters);
        this.workerGroupChangeNotifier.start();
    }

}
