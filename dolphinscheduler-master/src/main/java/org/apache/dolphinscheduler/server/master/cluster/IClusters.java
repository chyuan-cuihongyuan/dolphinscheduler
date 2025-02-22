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

import java.util.List;
import java.util.Optional;

/**
 * 集群
 *
 * @param <S> 服务器元数据
 */
public interface IClusters<S extends IClusters.IServerMetadata> {

    /**
     * 获取服务器集群
     *
     * @return 服务器集群
     */
    List<S> getServers();

    /**
     * 获取服务器集群
     *
     * @return 服务器集群
     */
    Optional<S> getServer(final String address);

    /**
     * 注册监听器
     *
     * @param listener 监听器
     */
    void registerListener(IClustersChangeListener<S> listener);

    /**
     * 服务器元数据
     */
    interface IServerMetadata {

        /**
         * 获取地址
         *
         * @return 地址
         */
        String getAddress();

        /**
         * 获取服务器状态
         *
         * @return 服务器状态
         */
        ServerStatus getServerStatus();

    }

    /**
     * 集群变更监听器
     *
     * @param <S> 服务器元数据
     */
    interface IClustersChangeListener<S extends IServerMetadata> {

        /**
         * 绑定服务器
         *
         * @param server 服务器
         */
        void onServerAdded(S server);

        /**
         * 移除服务器
         *
         * @param server 服务器
         */
        void onServerRemove(S server);

        /**
         * 更新服务器
         *
         * @param server 服务器
         */
        void onServerUpdate(S server);

    }

    /**
     * 服务器添加侦听器
     *
     * @param <S> 服务器元数据
     */
    interface ServerAddedListener<S extends IServerMetadata> extends IClustersChangeListener<S> {

        /**
         * 添加监听 移除服务器 操作
         *
         * @param server 服务器
         */
        @Override
        default void onServerRemove(S server) {
            // only care about server added
            // 只关心添加的服务器
        }

        /**
         * 添加监听 更新服务器 操作
         *
         * @param server 服务器
         */
        @Override
        default void onServerUpdate(S server) {
            // only care about server added
            // 只关心添加的服务器
        }

    }

    /**
     * 服务器删除侦听器
     *
     * @param <S> 服务器元数据
     */
    interface ServerRemovedListener<S extends IServerMetadata> extends IClustersChangeListener<S> {

        /**
         * 删除监听 添加服务器 操作
         *
         * @param server 服务器
         */
        @Override
        default void onServerAdded(S server) {
            // only care about server removed
            // 只关心添加的服务器
        }

        /**
         * 删除监听 移除服务器 操作
         *
         * @param server 服务器
         */
        @Override
        void onServerRemove(S server);

        /**
         * 删除监听 更新服务器 操作
         *
         * @param server 服务器
         */
        @Override
        default void onServerUpdate(S server) {
            // only care about server added
            // 只关心添加的服务器
        }

    }

}
