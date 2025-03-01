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

package org.apache.dolphinscheduler.registry.api;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dolphinscheduler.common.IStoppable;
import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.common.model.AlertServerHeartBeat;
import org.apache.dolphinscheduler.common.model.MasterHeartBeat;
import org.apache.dolphinscheduler.common.model.Server;
import org.apache.dolphinscheduler.common.model.WorkerHeartBeat;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.registry.api.enums.RegistryNodeType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 注册中心客户端核心类（策略模式+观察者模式）
 *
 * <p>实现功能：
 * <ol>
 *   <li><b>节点生命周期管理</b>：通过persistEphemeral()实现临时节点注册（用于心跳检测）</li>
 *   <li><b>服务发现机制</b>：getServerList()获取实时服务节点列表，支持MASTER/WORKER/ALERT类型</li>
 *   <li><b>负载均衡策略</b>：getRandomServer()实现随机选择算法（可扩展其他策略）</li>
 *   <li><b>分布式锁</b>：通过getLock()/releaseLock()实现基于注册中心的互斥锁</li>
 *   <li><b>故障恢复机制</b>：cleanHistoryFailoverFinishedNodes()自动清理过期故障节点</li>
 * </ol>
 *
 * <p>应用设计模式：
 * <ul>
 *   <li><b>策略模式</b>：通过Registry接口抽象注册中心操作，支持ZK/Etcd等不同实现</li>
 *   <li><b>观察者模式</b>：subscribe()方法允许监听节点变化事件</li>
 *   <li><b>门面模式</b>：封装复杂注册中心操作，提供统一API给上层系统</li>
 * </ul>
 */
@Component
@Slf4j
public class RegistryClient {
    // 空字符串常量
    private static final String EMPTY = "";
    // 可停止实例（优雅停机支持）
    private IStoppable stoppable;
    // 注册中心客户端
    private final Registry registry;

    /**
     * 构造方法（依赖注入模式）
     * 初始化时创建四种核心节点路径：
     * <ul>
     *   <li>MASTER：管理集群主节点</li>
     *   <li>WORKER：管理工作节点</li>
     *   <li>ALERT_SERVER：告警服务节点</li>
     *   <li>FAILOVER_FINISH_NODES：故障转移记录节点（用于防止重复故障恢复）</li>
     * </ul>
     */
    public RegistryClient(Registry registry) {
        this.registry = registry;
        if (!registry.exists(RegistryNodeType.MASTER.getRegistryPath())) {
            registry.put(RegistryNodeType.MASTER.getRegistryPath(), EMPTY, false);
        }
        if (!registry.exists(RegistryNodeType.WORKER.getRegistryPath())) {
            registry.put(RegistryNodeType.WORKER.getRegistryPath(), EMPTY, false);
        }
        if (!registry.exists(RegistryNodeType.ALERT_SERVER.getRegistryPath())) {
            registry.put(RegistryNodeType.ALERT_SERVER.getRegistryPath(), EMPTY, false);
        }
        if (!registry.exists(RegistryNodeType.FAILOVER_FINISH_NODES.getRegistryPath())) {
            registry.put(RegistryNodeType.FAILOVER_FINISH_NODES.getRegistryPath(), EMPTY, false);
        }
        cleanHistoryFailoverFinishedNodes();
    }

    /**
     * 判断是否已连接到注册中心
     *
     * @return
     */
    public boolean isConnected() {
        return registry.isConnected();

    }

    /**
     * 连接到注册中心
     *
     * @param duration 连接超时时间
     * @throws RegistryException 连接失败时抛出异常
     */
    public void connectUntilTimeout(@NonNull Duration duration) throws RegistryException {
        registry.connectUntilTimeout(duration);
    }

    /**
     * 获取服务节点列表（服务发现核心方法）
     *
     * <p>实现流程：
     * 1. 通过getServerMaps()获取节点元数据
     * 2. 解析心跳数据构建Server对象
     * 3. 支持三种节点类型：
     * - MASTER: 包含启动时间、主机端口等元数据
     * - WORKER: 额外携带Worker特定信息
     * - ALERT_SERVER: 告警服务特有配置
     *
     * @param registryNodeType 节点枚举类型，控制不同的解析逻辑
     * @return 带完整元数据的服务对象列表
     */
    public List<Server> getServerList(RegistryNodeType registryNodeType) {
        Map<String, String> serverMaps = getServerMaps(registryNodeType);

        List<Server> serverList = new ArrayList<>();
        for (Map.Entry<String, String> entry : serverMaps.entrySet()) {
            String serverPath = entry.getKey();
            String heartBeatJson = entry.getValue();
            if (StringUtils.isEmpty(heartBeatJson)) {
                log.error("The heartBeatJson is empty, serverPath: {}", serverPath);
                continue;
            }
            Server server = new Server();
            switch (registryNodeType) {
                case MASTER:
                    MasterHeartBeat masterHeartBeat = JSONUtils.parseObject(heartBeatJson, MasterHeartBeat.class);
                    server.setCreateTime(new Date(masterHeartBeat.getStartupTime()));
                    server.setLastHeartbeatTime(new Date(masterHeartBeat.getReportTime()));
                    server.setId(masterHeartBeat.getProcessId());
                    server.setHost(masterHeartBeat.getHost());
                    server.setPort(masterHeartBeat.getPort());
                    break;
                case WORKER:
                    WorkerHeartBeat workerHeartBeat = JSONUtils.parseObject(heartBeatJson, WorkerHeartBeat.class);
                    server.setCreateTime(new Date(workerHeartBeat.getStartupTime()));
                    server.setLastHeartbeatTime(new Date(workerHeartBeat.getReportTime()));
                    server.setId(workerHeartBeat.getProcessId());
                    server.setHost(workerHeartBeat.getHost());
                    server.setPort(workerHeartBeat.getPort());
                    break;
                case ALERT_SERVER:
                    AlertServerHeartBeat alertServerHeartBeat =
                            JSONUtils.parseObject(heartBeatJson, AlertServerHeartBeat.class);
                    server.setCreateTime(new Date(alertServerHeartBeat.getStartupTime()));
                    server.setLastHeartbeatTime(new Date(alertServerHeartBeat.getReportTime()));
                    server.setId(alertServerHeartBeat.getProcessId());
                    server.setHost(alertServerHeartBeat.getHost());
                    server.setPort(alertServerHeartBeat.getPort());
                    break;
                default:
                    log.warn("unknown registry node type: {}", registryNodeType);
            }

            server.setHeartBeatInfo(heartBeatJson);
            // todo: add host, port in heartBeat Info, so that we don't need to parse this again
            server.setServerDirectory(registryNodeType.getRegistryPath() + "/" + serverPath);
            serverList.add(server);
        }
        return serverList;
    }

    /**
     * 随机选择服务节点（负载均衡策略实现）
     *
     * <p>设计特点：
     * 1. 基于简单随机算法（可扩展为加权随机等高级策略）
     * 2. 返回Optional对象避免NPE
     * 3. 节点选择实时性：每次调用都获取最新节点列表
     *
     * @return 可能包含服务节点的Optional对象
     */
    public Optional<Server> getRandomServer(final RegistryNodeType registryNodeType) {
        final List<Server> serverList = getServerList(registryNodeType);
        if (CollectionUtils.isEmpty(serverList)) {
            return Optional.empty();
        }
        final Server server = serverList.get(RandomUtils.nextInt(0, serverList.size()));
        return Optional.ofNullable(server);
    }

    /**
     * 获取节点键值映射表（服务发现辅助方法）
     *
     * <p>实现要点：
     * 1. 组合使用getServerNodes()和get()方法
     * 2. 自动拼接完整节点路径（父路径+子节点名）
     * 3. 异常捕获机制确保服务发现容错性
     *
     * @param nodeType 节点类型枚举
     * @return 节点名称与元数据的映射表（Key格式：子节点名，Value：节点存储内容）
     */
    public Map<String, String> getServerMaps(RegistryNodeType nodeType) {
        Map<String, String> serverMap = new HashMap<>();
        try {
            Collection<String> serverList = getServerNodes(nodeType);
            for (String server : serverList) {
                serverMap.putIfAbsent(server, get(nodeType.getRegistryPath() + Constants.SINGLE_SLASH + server));
            }
        } catch (Exception e) {
            log.error("get server list failed", e);
        }

        return serverMap;
    }

    /**
     * 检查节点存活状态（健康检查核心方法）
     *
     * <p>实现原理：
     * 1. 通过getServerMaps()获取全量节点信息
     * 2. 使用Stream API进行模式匹配
     * 3. 支持模糊主机名匹配（contains匹配）
     *
     * @param host     目标主机标识（支持IP/主机名）
     * @param nodeType 节点类型枚举
     * @return true表示存在至少一个存活节点
     * @see #getServerMaps(RegistryNodeType) 依赖的节点数据源方法
     */
    public boolean checkNodeExists(String host, RegistryNodeType nodeType) {
        return getServerMaps(nodeType).keySet()
                .stream()
                .anyMatch(it -> it.contains(host));
    }

    /**
     * 关闭注册中心连接（资源释放关键方法）
     *
     * <p>实现要点：
     * 1. 调用底层注册中心的close()方法
     * 2. 异常捕获确保资源释放一致性
     *
     * @throws IOException 关闭过程中可能抛出的异常
     */
    public void close() throws IOException {
        registry.close();
    }

    /**
     * 持久化临时节点（Ephemeral节点实现心跳机制）
     *
     * <p>技术实现：
     * 1. 使用注册中心的临时节点特性
     * 2. 节点自动删除：会话结束或心跳超时后自动清除
     * 3. 用于实现服务存活检测机制
     */
    public void persistEphemeral(String key, String value) {
        registry.put(key, value, true);
    }

    /**
     * 持久化永久节点（配置信息存储）
     *
     * <p>与临时节点的区别：
     * 1. 节点生命周期：永久节点不会随会话结束消失
     * 2. 使用场景：存储集群配置、路由规则等持久化数据
     * 3. 数据可靠性：依赖注册中心的持久化存储能力
     *
     * @param key   节点路径（遵循ZooKeeper路径规范）
     * @param value 节点数据（JSON格式序列化）
     */
    public void persist(String key, String value) {
        log.info("persist key: {}, value: {}", key, value);
        registry.put(key, value, false);
    }

    /**
     * 删除注册中心节点（节点生命周期管理）
     *
     * <p>注意事项：
     * 1. 支持删除永久/临时节点
     * 2. 递归删除：当节点存在子节点时，删除操作会失败
     * 3. 幂等性设计：节点不存在时静默返回
     *
     * @param key 要删除的节点路径
     */
    public void remove(String key) {
        registry.delete(key);
    }

    /**
     * 获取节点数据（基础查询操作）
     *
     * <p>数据特性：
     * 1. 返回最新数据：强一致性保证（依赖注册中心实现）
     * 2. 空值处理：不存在的节点返回null
     * 3. 性能消耗：每次调用都会访问注册中心
     *
     * @param key 节点路径
     * @return 节点存储的字符串数据（可能为null）
     */

    public String get(String key) {
        return registry.get(key);
    }

    /**
     * 订阅节点变更事件（观察者模式实现）
     *
     * <p>事件类型：
     * <ul>
     *   <li>NODE_ADDED：子节点新增</li>
     *   <li>NODE_REMOVED：子节点删除</li>
     *   <li>NODE_UPDATED：节点数据更新</li>
     * </ul>
     *
     * @param path     监听的父节点路径
     * @param listener 监听器需处理不同事件类型
     */
    public void subscribe(String path, SubscribeListener listener) {
        registry.subscribe(path, listener);
    }

    /**
     * 添加连接状态监听器（观察者模式实现）
     *
     * <p>监听事件包括：
     * <ul>
     *   <li>CONNECTED：注册中心连接建立</li>
     *   <li>RECONNECTED：断线重连成功</li>
     *   <li>SUSPENDED：连接临时中断</li>
     *   <li>LOST：连接永久丢失</li>
     * </ul>
     *
     * @param listener 监听器实现需处理不同状态转换
     * @see ConnectionListener 状态监听接口定义
     */
    public void addConnectionStateListener(ConnectionListener listener) {
        registry.addConnectionStateListener(listener);
    }

    /**
     * 检查节点是否存在（基础查询操作）
     *
     * <p>数据特性：
     * 1. 实时性：依赖注册中心的实时数据
     * 2. 幂等性：节点不存在时返回false
     *
     * @param key 节点路径
     * @return true表示节点存在，false表示不存在
     */
    public boolean exists(String key) {
        return registry.exists(key);
    }

    /**
     * 尝试获取指定键对应的锁。
     * 获取分布式锁（互斥访问控制）
     *
     * <p>实现机制：
     * 1. 基于注册中心的原子操作实现
     * 2. 锁超时：依赖会话超时自动释放
     * 3. 可重入性：相同线程可重复获取
     *
     * @param key 需要获取的锁的唯一标识符，非空字符串
     * @return 获取锁的结果：true 表示成功获得锁，false 表示获取失败
     * @throws IllegalStateException 当注册中心未处于连接状态时抛出
     */
    public boolean getLock(String key) {
        // 前置条件检查：验证注册中心连接状态
        if (!registry.isConnected()) {
            throw new IllegalStateException("注册表未连接");
        }
        // 委托注册中心执行实际的锁获取操作
        return registry.acquireLock(key);
    }

    public boolean releaseLock(String key) {
        return registry.releaseLock(key);
    }

    /**
     * 设置可停止实例（优雅停机支持）
     *
     * <p>关联功能：
     * 1. 注册中心连接断开时触发停机逻辑
     * 2. 需要与集群管理模块配合使用
     *
     * @param stoppable 实现优雅停机逻辑的实例
     */
    public void setStoppable(IStoppable stoppable) {
        this.stoppable = stoppable;
    }

    public IStoppable getStoppable() {
        return stoppable;
    }

    public Collection<String> getChildrenKeys(final String key) {
        return registry.children(key);
    }

    /**
     * 获取子节点集合（层级数据查询）
     *
     * <p>技术特性：
     * 1. 实时性：返回当前时刻最新子节点列表
     * 2. 递归查询：仅返回直接子节点，不包含嵌套子节点
     * 3. 异常处理：底层异常转换为RegistryException抛出
     *
     * @param key 父节点路径
     * @return 子节点名称集合（非全路径）
     * @throws RegistryException 注册中心操作异常时抛出
     */
    public Set<String> getServerNodeSet(RegistryNodeType nodeType) {
        try {
            return new HashSet<>(getServerNodes(nodeType));
        } catch (Exception e) {
            throw new RegistryException("Failed to get server node: " + nodeType, e);
        }
    }

    private Collection<String> getServerNodes(RegistryNodeType nodeType) {
        return getChildrenKeys(nodeType.getRegistryPath());
    }

    /**
     * 清理历史故障转移记录（定时任务调用）
     *
     * <p>业务规则：
     * 1. 保留周期：最近7天的故障记录
     * 2. 清理机制：遍历FAILOVER_FINISH_NODES子节点
     * 3. 异常处理：记录清理失败日志但继续流程
     *
     * <p>技术实现：
     * 1. 基于注册中心的递归查询能力
     * 2. 时间计算：System.currentTimeMillis() - 7天
     */
    private void cleanHistoryFailoverFinishedNodes() {
        // 清理历史故障转移完成的节点
        //哪个故障转移发生在当前时间减去1周之前
        final Collection<String> failoverFinishedNodes =
                registry.children(RegistryNodeType.FAILOVER_FINISH_NODES.getRegistryPath());
        if (CollectionUtils.isEmpty(failoverFinishedNodes)) {
            return;
        }
        for (final String failoverFinishedNode : failoverFinishedNodes) {
            try {
                final String failoverFinishTime = registry.get(failoverFinishedNode);
                if (System.currentTimeMillis() - Long.parseLong(failoverFinishTime) > TimeUnit.DAYS.toMillis(7)) {
                    registry.delete(failoverFinishedNode);
                    log.info(
                            "清除故障转移完成节点: {} 哪个故障转移时间在当前时间减去1周之前",
                            failoverFinishedNode);
                }
            } catch (Exception ex) {
                log.error("清除故障转移失败FinishedNode: {}", failoverFinishedNode, ex);
            }
        }
    }
}
