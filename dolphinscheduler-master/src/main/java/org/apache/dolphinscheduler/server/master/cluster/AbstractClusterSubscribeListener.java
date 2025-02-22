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

import org.apache.dolphinscheduler.registry.api.Event;
import org.apache.dolphinscheduler.registry.api.SubscribeListener;

import lombok.extern.slf4j.Slf4j;

/**
 * 抽象集群订阅监听器
 *
 * @param <T>
 */
@Slf4j
public abstract class AbstractClusterSubscribeListener<T extends BaseServerMetadata> implements SubscribeListener {

    /**
     * 通知
     *
     * @param event 活动
     */
    @Override
    public void notify(Event event) {
        try {
            // make sure the event is processed in order
            synchronized (this) {
                Event.Type type = event.getType();
                T server = parseServerFromHeartbeat(event.getEventData());
                if (server == null) {
                    // log.error("Unknown cluster change event: {}", event);
                    log.error("未知群集更改事件: ｛｝", event);
                    return;
                }
                switch (type) {
                    case ADD:
                        log.info("Server {} added", server);
                        onServerAdded(server);
                        break;
                    case REMOVE:
                        log.warn("Server {} removed", server);
                        onServerRemove(server);
                        break;
                    case UPDATE:
                        log.debug("Server {} updated", server);
                        onServerUpdate(server);
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception ex) {
            log.error("Notify cluster change event: {} failed", event, ex);
        }
    }

    /**
     * 获取订阅范围
     *
     * @return 获取订阅范围
     */
    @Override
    public SubscribeScope getSubscribeScope() {
        return SubscribeScope.CHILDREN_ONLY; // 观察路径及其所有子路径和父路径
    }

    /**
     * 从心跳解析服务器
     *
     * @param serverHeartBeatJson 服务器心跳json
     * @return 服务器
     */
    abstract T parseServerFromHeartbeat(String serverHeartBeatJson);

    /**
     * 已添加的服务器
     *
     * @param serverHeartBeat 服务器心跳
     */
    public abstract void onServerAdded(T serverHeartBeat);

    /**
     * 已删除的服务器
     *
     * @param serverHeartBeat 服务器心跳
     */
    public abstract void onServerRemove(T serverHeartBeat);

    /**
     * 已更新的服务器
     *
     * @param serverHeartBeat 服务器心跳
     */
    public abstract void onServerUpdate(T serverHeartBeat);

}
