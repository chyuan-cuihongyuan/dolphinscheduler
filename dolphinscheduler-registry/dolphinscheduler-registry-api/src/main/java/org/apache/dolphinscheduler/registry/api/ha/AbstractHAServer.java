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

package org.apache.dolphinscheduler.registry.api.ha;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.dolphinscheduler.common.thread.ThreadUtils;
import org.apache.dolphinscheduler.registry.api.Event;
import org.apache.dolphinscheduler.registry.api.Registry;
import org.apache.dolphinscheduler.registry.api.SubscribeListener;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * 高可用服务抽象基类（状态模式 + 观察者模式）
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>Leader选举管理</b>：通过分布式锁实现选主</li>
 *   <li><b>状态机维护</b>：管理ACTIVE/STAND_BY状态转换</li>
 *   <li><b>故障自动恢复</b>：内置重试机制应对网络抖动</li>
 * </ol>
 *
 * <p>关键配置参数：
 * <ul>
 *   <li>selectorPath：选主路径（Zookeeper格式）</li>
 *   <li>serverIdentify：服务唯一标识（格式：IP:PORT）</li>
 *   <li>DEFAULT_RETRY_INTERVAL：选举重试间隔（5秒）</li>
 * </ul>
 */
@Slf4j
public abstract class AbstractHAServer implements HAServer {

    /**
     * 注册中心客户端（支持Zookeeper/Nacos等）
     */
    private final Registry registry;

    /**
     * 选主路径（持久节点路径）
     * <p>示例：/dolphinscheduler/nodes/master
     */
    private final String selectorPath;

    /**
     * 服务实例唯一标识（推荐格式：IP:PORT）
     */
    private final String serverIdentify;

    /**
     * 当前服务状态（原子状态）
     */
    private ServerStatus serverStatus;

    /**
     * 状态变更监听器列表（支持扩展）
     */
    private final List<ServerStatusChangeListener> serverStatusChangeListeners;

    private static final long DEFAULT_RETRY_INTERVAL = 5_000;

    private static final int DEFAULT_MAX_RETRY_TIMES = 20;

    /**
     * 构造器（模板方法模式）
     *
     * @param registry       注册中心实例
     * @param selectorPath   选主路径（必须存在）
     * @param serverIdentify 服务实例标识（非空）
     */
    public AbstractHAServer(final Registry registry, final String selectorPath, final String serverIdentify) {
        this.registry = registry;
        this.selectorPath = checkNotNull(selectorPath);
        this.serverIdentify = checkNotNull(serverIdentify);
        this.serverStatus = ServerStatus.STAND_BY;
        this.serverStatusChangeListeners = Lists.newArrayList(new DefaultServerStatusChangeListener());
    }

    /**
     * 启动服务（自动触发选主）
     * <p>执行流程：
     * 1. 订阅选主路径变更事件
     * 2. 参与首次选主
     * 3. 根据选举结果更新状态
     */
    @Override
    public void start() {
        registry.subscribe(selectorPath, new SubscribeListener() {

            @Override
            public void notify(Event event) {
                if (Event.Type.REMOVE.equals(event.getType())) {
                    if (serverIdentify.equals(event.getEventData())) {
                        statusChange(ServerStatus.STAND_BY);
                    } else {
                        if (participateElection()) {
                            statusChange(ServerStatus.ACTIVE);
                        }
                    }
                }
            }

            @Override
            public SubscribeScope getSubscribeScope() {
                return SubscribeScope.PATH_ONLY;
            }
        });

        if (participateElection()) {
            statusChange(ServerStatus.ACTIVE);
        } else {
            log.info("Server {} is standby", serverIdentify);
        }
    }

    @Override
    public boolean isActive() {
        return ServerStatus.ACTIVE.equals(getServerStatus());
    }

    /**
     * 参与选举（带重试机制）
     * <p>选举逻辑：
     * 1. 获取分布式锁（防止脑裂）
     * 2. 检查选主路径是否存在
     * - 不存在：创建节点并成为Leader
     * - 存在：验证当前节点是否为Leader
     * 3. 释放锁
     */
    @Override
    public boolean participateElection() {
        final String electionLock = selectorPath + "-lock";
        //如果在参与选举期间遇到异常，将重试。
        //这可以避免服务器因网络抖动而未被选为领导者的情况。
        for (int i = 0; i < DEFAULT_MAX_RETRY_TIMES; i++) {
            try {
                try {
                    if (registry.acquireLock(electionLock)) {
                        if (!registry.exists(selectorPath)) {
                            registry.put(selectorPath, serverIdentify, true);
                            return true;
                        }
                        return serverIdentify.equals(registry.get(selectorPath));
                    }
                    return false;
                } finally {
                    registry.releaseLock(electionLock);
                }
            } catch (Exception e) {
                log.error("Participate election error, meet an exception, will retry after {}ms",
                        DEFAULT_RETRY_INTERVAL, e);
                ThreadUtils.sleep(DEFAULT_RETRY_INTERVAL);
            }
        }
        throw new IllegalStateException(
                "Participate election failed after retry " + DEFAULT_MAX_RETRY_TIMES + " times");
    }

    @Override
    public void addServerStatusChangeListener(ServerStatusChangeListener listener) {
        serverStatusChangeListeners.add(listener);
    }

    @Override
    public ServerStatus getServerStatus() {
        return serverStatus;
    }

    private void statusChange(ServerStatus targetStatus) {
        final ServerStatus originStatus = serverStatus;
        serverStatus = targetStatus;
        synchronized (this) {
            try {
                serverStatusChangeListeners.forEach(listener -> listener.change(originStatus, serverStatus));
            } catch (Exception ex) {
                log.error("触发服务器StatusChangeListener从 {} -> {} error", originStatus, targetStatus, ex);
            }
        }
    }
}
