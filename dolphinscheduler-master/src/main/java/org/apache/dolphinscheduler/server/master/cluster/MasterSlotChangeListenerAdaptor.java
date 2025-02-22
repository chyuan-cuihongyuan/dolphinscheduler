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

import java.util.List;

/**
 * 主服务器槽位变更监听适配器，用于将主服务器集群变更事件转换为槽位变更事件
 * <p>实现IMasterSlotChangeListener和IClustersChangeListener接口，监听主服务器集群状态变化，
 * 并触发主服务器槽位的重新平衡逻辑</p>
 */
public class MasterSlotChangeListenerAdaptor
        implements
            IMasterSlotChangeListener,
            IClusters.IClustersChangeListener<MasterServerMetadata> {

    /** 主服务器槽位管理器，用于执行槽位重新平衡操作 */
    private final MasterSlotManager masterSlotManager;

    /** 主服务器集群管理器，用于获取当前正常状态的主服务器列表 */
    private final MasterClusters masterClusters;

    /**
     * 构造方法，初始化槽位管理和集群管理组件
     * @param masterSlotManager 主服务器槽位管理器实例，不可为空
     * @param masterClusters 主服务器集群管理组件实例，不可为空
     */
    public MasterSlotChangeListenerAdaptor(final MasterSlotManager masterSlotManager,
                                           final MasterClusters masterClusters) {
        this.masterSlotManager = masterSlotManager;
        this.masterClusters = masterClusters;
    }

    /**
     * 主服务器槽位变更事件处理
     * @param normalMasterServers 当前处于正常状态的主服务器元数据列表
     */
    @Override
    public void onMasterSlotChanged(final List<MasterServerMetadata> normalMasterServers) {
        // 触发槽位重新分配的核心操作
        masterSlotManager.doReBalance(normalMasterServers);
    }

    /**
     * 主服务器新增事件处理
     * @param server 新增的主服务器元数据
     */
    @Override
    public void onServerAdded(MasterServerMetadata server) {
        // 当集群新增节点时触发槽位调整
        onMasterSlotChanged(masterClusters.getNormalServers());
    }

    /**
     * 主服务器移除事件处理
     * @param server 被移除的主服务器元数据
     */
    @Override
    public void onServerRemove(MasterServerMetadata server) {
        // 当集群移除节点时触发槽位调整
        onMasterSlotChanged(masterClusters.getNormalServers());
    }

    /**
     * 主服务器更新事件处理
     * @param server 更新后的主服务器元数据
     */
    @Override
    public void onServerUpdate(MasterServerMetadata server) {
        // 当节点元数据变更时触发槽位调整
        onMasterSlotChanged(masterClusters.getNormalServers());
    }
}