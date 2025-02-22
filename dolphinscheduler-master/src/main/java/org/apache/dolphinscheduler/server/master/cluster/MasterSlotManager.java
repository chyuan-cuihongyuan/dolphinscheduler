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

import org.apache.dolphinscheduler.server.master.config.MasterConfig;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * 主节点槽位管理器，负责主节点槽位的分配和再平衡
 * <p>根据当前可用主节点集群状态动态调整当前槽位和总槽位数
 */
@Slf4j
@Component
public class MasterSlotManager implements IMasterSlotReBalancer {

    /** Master 节点配置信息 */
    private final MasterConfig masterConfig;

    /** 当前节点分配的槽位编号（-1 表示未分配） */
    private volatile int currentSlot = -1;

    /** 集群总槽位数 */
    private volatile int totalSlots = 0;

    /**
     * 构造方法
     * @param masterConfig 主节点配置信息，包含当前主节点地址等重要参数
     */
    public MasterSlotManager(final MasterConfig masterConfig) {
        this.masterConfig = masterConfig;
    }

    /**
     * 获取当前主节点分配的槽位号 获取当前主插槽，如果插槽为-1，则表示主插槽不可用。
     * @return 当前槽位号（-1表示未分配有效槽位）
     */
    public int getCurrentMasterSlot() {
        return currentSlot;
    }

    /**
     * 获取总槽位数
     * @return 当前集群划分的总槽位数量
     */
    public int getTotalMasterSlots() {
        return totalSlots;
    }

    /**
     * 验证当前槽位是否有效
     * @return 当总槽位数>0且当前槽位号>=0时返回true
     */
    public boolean checkSlotValid() {
        return totalSlots > 0 && currentSlot >= 0;
    }

    /**
     * 执行主节点槽位再平衡
     * @param normalMasterServers 当前处于正常状态的主节点集群元数据列表
     * <p>方法逻辑：
     * 1. 在可用节点列表中定位当前主节点
     * 2. 当节点丢失时标记槽位失效
     * 3. 仅当槽位信息变化时执行更新
     */
    @Override
    public void doReBalance(List<MasterServerMetadata> normalMasterServers) {

        // 在正常节点列表中搜索当前主节点地址
        int tmpCurrentSlot = -1;
        for (int i = 0; i < normalMasterServers.size(); i++) {
            if (normalMasterServers.get(i).getAddress().equals(masterConfig.getMasterAddress())) {
                tmpCurrentSlot = i;
                break;
            }
        }

        // 处理当前主节点不在集群中的异常情况
        if (tmpCurrentSlot == -1) {
            log.warn(
//                    "Do rebalance failed, cannot found the current master: {} in the normal master clusters: {}. Please check the current master server status",
                    "重新平衡失败，在正常主群集: ｛｝中找不到当前主服务器: ｛｝。请检查当前主服务器状态",
                    masterConfig.getMasterAddress(), normalMasterServers);
            currentSlot = -1;
            return;
        }

        // 跳过无变化的再平衡操作
        if (totalSlots == normalMasterServers.size() && currentSlot == tmpCurrentSlot) {
//            log.debug("No need to rebalance, the currentSlot: {}, totalSlots: {} doesn't changed", currentSlot,
            log.debug("无需重新平衡，当前Slot:｛｝，totalSlots:｛｝没有变化", currentSlot,
                    totalSlots);
            return;
        }

        // 更新槽位信息
        totalSlots = normalMasterServers.size();
        currentSlot = tmpCurrentSlot;
//        log.info("Do rebalance success, current master slot: {}, total master slots: {}", currentSlot, totalSlots);
        log.info("重新平衡成功，当前主插槽：｛｝，主插槽总数：｛｝", currentSlot, totalSlots);
    }
}
