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

import org.apache.dolphinscheduler.common.enums.ServerStatus;
import org.apache.dolphinscheduler.common.model.WorkerHeartBeat;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.dao.entity.WorkerGroup;
import org.apache.dolphinscheduler.dao.utils.WorkerGroupUtils;

import org.apache.commons.collections4.list.UnmodifiableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 工作集群
 */
public class WorkerClusters extends AbstractClusterSubscribeListener<WorkerServerMetadata>
        implements
            IClusters<WorkerServerMetadata>,
            WorkerGroupChangeNotifier.WorkerGroupListener {

    // WorkerIdentifier(workerAddress) -> worker 工作标识符(工作服务器地址) -> 工作映射
    private final Map<String, WorkerServerMetadata> workerMapping = new ConcurrentHashMap<>();

    // WorkerGroup from db -> WorkerIdentifier(workerAddress) 数据库工作组映射
    private final Map<String, List<String>> dbWorkerGroupMapping = new ConcurrentHashMap<>();

    // WorkerGroup from config -> WorkerIdentifier(workerAddress) 配置工作组映射
    private final Map<String, List<String>> configWorkerGroupMapping = new ConcurrentHashMap<>();

    // worker集群更改侦听器
    private final List<IClustersChangeListener<WorkerServerMetadata>> workerClusterChangeListeners =
            new CopyOnWriteArrayList<>();

    /**
     * 获取所有工作服务器
     *
     * @return 所有工作服务器
     */
    @Override
    public List<WorkerServerMetadata> getServers() {
        return UnmodifiableList.unmodifiableList(new ArrayList<>(workerMapping.values()));
    }

    /**
     * 获取工作服务器
     *
     * @param address 工作服务器地址
     * @return 工作服务器
     */
    @Override
    public Optional<WorkerServerMetadata> getServer(final String address) {
        return Optional.ofNullable(workerMapping.get(address));
    }

    /**
     * 获取数据库中的所有工作服务器
     *
     * @param workerGroup 工作组
     * @return 数据库中的工作服务器
     */
    public List<String> getDbWorkerServerAddressByGroup(String workerGroup) {
        if (WorkerGroupUtils.getDefaultWorkerGroup().equals(workerGroup)) {
            return UnmodifiableList.unmodifiableList(new ArrayList<>(workerMapping.keySet()));
        }
        return dbWorkerGroupMapping.getOrDefault(workerGroup, Collections.emptyList());
    }

    /**
     * 获取配置中的所有工作服务器
     *
     * @param workerGroup 工作组
     * @return 配置中的工作服务器
     */
    public List<String> getConfigWorkerServerAddressByGroup(String workerGroup) {
        if (WorkerGroupUtils.getDefaultWorkerGroup().equals(workerGroup)) {
            return UnmodifiableList.unmodifiableList(new ArrayList<>(workerMapping.keySet()));
        }
        return configWorkerGroupMapping.getOrDefault(workerGroup, Collections.emptyList());
    }

    /**
     * 获取正常工作服务器
     *
     * @param workerGroup 工作组
     * @return 正常的工作服务器
     */
    public List<String> getNormalWorkerServerAddressByGroup(String workerGroup) {
        List<String> dbWorkerAddresses = getDbWorkerServerAddressByGroup(workerGroup)
                .stream()
                .map(workerMapping::get)
                .filter(Objects::nonNull)
                .filter(workerServer -> workerServer.getServerStatus() == ServerStatus.NORMAL)
                .map(WorkerServerMetadata::getAddress)
                .collect(Collectors.toList());
        List<String> configWorkerAddresses = getConfigWorkerServerAddressByGroup(workerGroup)
                .stream()
                .map(workerMapping::get)
                .filter(Objects::nonNull)
                .filter(workerServer -> workerServer.getServerStatus() == ServerStatus.NORMAL)
                .map(WorkerServerMetadata::getAddress)
                .collect(Collectors.toList());
        dbWorkerAddresses.removeAll(configWorkerAddresses);
        dbWorkerAddresses.addAll(configWorkerAddresses);
        return UnmodifiableList.unmodifiableList(dbWorkerAddresses);
    }

    /**
     * 判断是否包含工作组
     *
     * @param workerGroup 工作组
     * @return 是否包含工作组
     */
    public boolean containsWorkerGroup(String workerGroup) {
        return WorkerGroupUtils.getDefaultWorkerGroup().equals(workerGroup)
                || dbWorkerGroupMapping.containsKey(workerGroup)
                || configWorkerGroupMapping.containsKey(workerGroup);
    }

    /**
     * 注册监听器
     *
     * @param listener 监听器
     */
    @Override
    public void registerListener(IClustersChangeListener<WorkerServerMetadata> listener) {
        workerClusterChangeListeners.add(listener);
    }

    /**
     * 删除工作组
     *
     * @param workerGroups 工作组
     */
    @Override
    public void onWorkerGroupDelete(List<WorkerGroup> workerGroups) {
        synchronized (dbWorkerGroupMapping) {
            for (WorkerGroup workerGroup : workerGroups) {
                dbWorkerGroupMapping.remove(workerGroup.getName());
            }
        }
    }

    /**
     * 新增工作组
     *
     * @param workerGroups 工作组
     */
    @Override
    public void onWorkerGroupAdd(List<WorkerGroup> workerGroups) {
        // The logic of adding WorkerGroup is the same as updating WorkerGroup
        // Both need to change the WorkerGroup mapping to the latest
        // 添加WorkerGroup的逻辑与更新WorkerGroup相同
        // 两者都需要将WorkerGroup映射更改为最新
        onWorkerGroupChange(workerGroups);
    }

    /**
     * 更新工作组
     *
     * @param workerGroups 工作组
     */
    @Override
    public void onWorkerGroupChange(List<WorkerGroup> workerGroups) {
        for (WorkerGroup workerGroup : workerGroups) {
            List<String> activeWorkers = WorkerGroupUtils.getWorkerAddressListFromWorkerGroup(workerGroup)
                    .stream()
                    .map(workerMapping::get)
                    .filter(Objects::nonNull)
                    .map(WorkerServerMetadata::getAddress)
                    .collect(Collectors.toList());
            synchronized (dbWorkerGroupMapping) {
                dbWorkerGroupMapping.put(workerGroup.getName(), activeWorkers);
            }
        }
    }

    /**
     * 解析服务器心跳
     *
     * @param serverHeartBeatJson 服务器心跳json
     * @return 服务器元数据
     */
    @Override
    WorkerServerMetadata parseServerFromHeartbeat(String serverHeartBeatJson) {
        WorkerHeartBeat workerHeartBeat = JSONUtils.parseObject(serverHeartBeatJson, WorkerHeartBeat.class);
        if (workerHeartBeat == null) {
            return null;
        }
        return WorkerServerMetadata.parseFromHeartBeat(workerHeartBeat);
    }

    /**
     * 新增服务器
     *
     * @param workerServer 服务器心跳
     */
    @Override
    public void onServerAdded(WorkerServerMetadata workerServer) {
        workerMapping.put(workerServer.getAddress(), workerServer);
        synchronized (configWorkerGroupMapping) {
            List<String> addWorkerGroupAddrList = configWorkerGroupMapping.get(workerServer.getWorkerGroup());
            if (addWorkerGroupAddrList == null) {
                List<String> newWorkerGroupAddrList = new ArrayList<>();
                newWorkerGroupAddrList.add(workerServer.getAddress());
                configWorkerGroupMapping.put(workerServer.getWorkerGroup(), newWorkerGroupAddrList);
            } else if (!addWorkerGroupAddrList.contains(workerServer.getAddress())) {
                addWorkerGroupAddrList.add(workerServer.getAddress());
                configWorkerGroupMapping.put(workerServer.getWorkerGroup(), addWorkerGroupAddrList);
            }
        }
        for (IClustersChangeListener<WorkerServerMetadata> listener : workerClusterChangeListeners) {
            listener.onServerAdded(workerServer);
        }
    }

    /**
     * 删除服务器
     *
     * @param workerServer 服务器心跳
     */
    @Override
    public void onServerRemove(WorkerServerMetadata workerServer) {
        workerMapping.remove(workerServer.getAddress(), workerServer);
        synchronized (configWorkerGroupMapping) {
            List<String> removeWorkerGroupAddrList = configWorkerGroupMapping.get(workerServer.getWorkerGroup());
            if (removeWorkerGroupAddrList != null && removeWorkerGroupAddrList.contains(workerServer.getAddress())) {
                removeWorkerGroupAddrList.remove(workerServer.getAddress());
                if (removeWorkerGroupAddrList.isEmpty()) {
                    configWorkerGroupMapping.remove(workerServer.getWorkerGroup());
                }
            }
        }
        for (IClustersChangeListener<WorkerServerMetadata> listener : workerClusterChangeListeners) {
            listener.onServerRemove(workerServer);
        }
    }

    /**
     * 更新服务器
     *
     * @param workerServer 服务器心跳
     */
    @Override
    public void onServerUpdate(WorkerServerMetadata workerServer) {
        workerMapping.put(workerServer.getAddress(), workerServer);
        for (IClustersChangeListener<WorkerServerMetadata> listener : workerClusterChangeListeners) {
            listener.onServerUpdate(workerServer);
        }
    }
}
