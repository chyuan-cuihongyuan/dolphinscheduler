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
import org.apache.dolphinscheduler.common.model.MasterHeartBeat;
import org.apache.dolphinscheduler.common.utils.JSONUtils;

import org.apache.commons.collections4.list.UnmodifiableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * 主集群
 */
@Slf4j
public class MasterClusters extends AbstractClusterSubscribeListener<MasterServerMetadata>
        implements
            IClusters<MasterServerMetadata> {

    /**
     * Master address -> MasterServer 主地址->主服务器
     **/
    private final Map<String, MasterServerMetadata> masterServerMap = new ConcurrentHashMap<>();

    /**
     * 主集群更改侦听器
     * <p>
     * CopyOnWriteArrayList 是 Java 并发包中线程安全的 List 实现类，适用于读多写少的并发场景
     * 监听器列表（如事件监听器）：高频遍历，低频修改。
     * 缓存：读远多于写，容忍短暂数据不一致。
     * 配置信息存储：配置变更少，读取频繁。
     */
    private final List<IClustersChangeListener<MasterServerMetadata>> masterClusterChangeListeners =
            new CopyOnWriteArrayList<>();

    /**
     * 获取所有主服务器
     *
     * @return 主服务器列表
     */
    @Override
    public List<MasterServerMetadata> getServers() {
        return UnmodifiableList.unmodifiableList(new ArrayList<>(masterServerMap.values()));
    }

    /**
     * 根据主服务器地址获取主服务器
     *
     * @param address 主服务器地址
     * @return 主服务器
     */
    @Override
    public Optional<MasterServerMetadata> getServer(final String address) {
        return Optional.ofNullable(masterServerMap.get(address));
    }

    /**
     * 获取所有正常主服务器
     *
     * @return 正常主服务器列表
     */
    public List<MasterServerMetadata> getNormalServers() {
        List<MasterServerMetadata> normalMasterServers = masterServerMap.values()
                .stream()
                .filter(masterServer -> masterServer.getServerStatus() == ServerStatus.NORMAL)
                .collect(Collectors.toList());
        return UnmodifiableList.unmodifiableList(normalMasterServers);
    }

    /**
     * 注册监听器
     *
     * @param listener 监听器
     */
    @Override
    public void registerListener(final IClustersChangeListener<MasterServerMetadata> listener) {
        masterClusterChangeListeners.add(listener);
    }

    /**
     * 解析服务器心跳
     *
     * @param masterHeartBeatJson 服务器心跳json
     * @return 主服务器元数据
     */
    @Override
    MasterServerMetadata parseServerFromHeartbeat(final String masterHeartBeatJson) {
        MasterHeartBeat masterHeartBeat = JSONUtils.parseObject(masterHeartBeatJson, MasterHeartBeat.class);
        if (masterHeartBeat == null) {
            return null;
        }
        return MasterServerMetadata.parseFromHeartBeat(masterHeartBeat);
    }

    /**
     * 服务器添加
     *
     * @param masterServer 服务器心跳
     */
    @Override
    public void onServerAdded(final MasterServerMetadata masterServer) {
        masterServerMap.put(masterServer.getAddress(), masterServer);
        for (IClustersChangeListener<MasterServerMetadata> listener : masterClusterChangeListeners) {
            listener.onServerAdded(masterServer);
        }
    }

    /**
     * 服务器移除
     *
     * @param masterServer 服务器心跳
     */
    @Override
    public void onServerRemove(final MasterServerMetadata masterServer) {
        masterServerMap.remove(masterServer.getAddress());
        for (IClustersChangeListener<MasterServerMetadata> listener : masterClusterChangeListeners) {
            listener.onServerRemove(masterServer);
        }
    }

    /**
     * 服务器更新
     *
     * @param masterServer 服务器心跳
     */
    @Override
    public void onServerUpdate(final MasterServerMetadata masterServer) {
        masterServerMap.put(masterServer.getAddress(), masterServer);
        for (IClustersChangeListener<MasterServerMetadata> listener : masterClusterChangeListeners) {
            listener.onServerUpdate(masterServer);
        }
    }

}
