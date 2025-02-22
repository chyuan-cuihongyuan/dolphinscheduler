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
 * 主插槽变更监听器接口，用于在主插槽(Master Slot)发生变更时接收通知
 */
public interface IMasterSlotChangeListener {

    /**
     * 当主插槽配置发生变更时触发回调
     *
     * @param normalMasterServers 当前可用的正常主服务器元数据列表。
     *                            包含最新状态的主服务器集群信息，列表顺序可能具有业务意义。
     *                            参数类型为 MasterServerMetadata 的集合，封装主服务器的核心元数据。
     */
    void onMasterSlotChanged(final List<MasterServerMetadata> normalMasterServers);
}
