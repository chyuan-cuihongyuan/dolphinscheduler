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

package org.apache.dolphinscheduler.server.master.registry;

import lombok.extern.slf4j.Slf4j;
import org.apache.dolphinscheduler.common.IStoppable;
import org.apache.dolphinscheduler.common.enums.ServerStatus;
import org.apache.dolphinscheduler.common.model.MasterHeartBeat;
import org.apache.dolphinscheduler.common.thread.ThreadUtils;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.common.utils.NetUtils;
import org.apache.dolphinscheduler.meter.metrics.MetricsProvider;
import org.apache.dolphinscheduler.registry.api.RegistryClient;
import org.apache.dolphinscheduler.registry.api.RegistryException;
import org.apache.dolphinscheduler.registry.api.enums.RegistryNodeType;
import org.apache.dolphinscheduler.server.master.config.MasterConfig;
import org.apache.dolphinscheduler.server.master.engine.MasterCoordinator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.apache.dolphinscheduler.common.constants.Constants.SLEEP_TIME_MILLIS;

/**
 * Master节点注册中心客户端（核心注册管理组件）
 * <p>DolphinScheduler主注册客户端，用于连接到注册表并传递注册表事件。
 * <p>当主节点启动时，它将在注册表中心注册。并启动{@link MasterHeartBeatTask}以更新注册表中的元数据。
 * <p>设计模式：
 * <ol>
 *    <li>1. 观察者模式 - 通过ConnectionStateListener监听注册中心连接状态变化</li>
 *    <li>2. 命令模式   - HeartBeatTask封装心跳检测逻辑作为独立命令</li>
 *    <li>3. 门面模式   - 封装Zookeeper等注册中心底层操作，提供高层接口</li>
 *    <li>4. 自动资源管理 - 实现AutoCloseable接口支持try-with-resources语法</li>
 *  </ol>
 */
@Component
@Slf4j
public class MasterRegistryClient implements AutoCloseable {

    // 注册中心客户端门面
    @Autowired// 依赖注入组件（控制反转模式）
    private RegistryClient registryClient;
    // Master节点配置中心
    @Autowired
    private MasterConfig masterConfig;
    // 指标度量采集器
    @Autowired
    private MetricsProvider metricsProvider;
    // Master协调控制器
    @Autowired
    private MasterCoordinator masterCoordinator;
    // 心跳检测任务（后台守护线程）
    private MasterHeartBeatTask masterHeartBeatTask;

    /**
     * 主启动方法（生命周期管理）
     * 功能流程：
     * 1. 初始化心跳检测任务
     * 2. 执行节点注册
     * 3. 注册连接状态监听器
     * 异常处理：启动失败时抛出RegistryException
     */
    public void start() {
        try {
            this.masterHeartBeatTask =
                    new MasterHeartBeatTask(masterConfig, metricsProvider, registryClient, masterCoordinator);
            // master registry 核心注册逻辑
            registry();
            registryClient.addConnectionStateListener(new MasterConnectionStateListener(registryClient));
        } catch (Exception e) {
            throw new RegistryException("主注册表客户端启动错误", e);
        }
    }

    /**
     * 注册中心连接状态监听器设置方法
     * @param stoppable 停止接口
     */
    public void setRegistryStoppable(IStoppable stoppable) {
        registryClient.setStoppable(stoppable);
    }

    /**
     * 资源关闭方法（AutoCloseable实现）
     * 组合操作：
     * - 停止心跳检测
     * - 执行节点注销
     * - 日志跟踪关闭状态
     */
    @Override
    public void close() {
        // TODO unsubscribe MasterRegistryDataListener
        if (masterHeartBeatTask != null) {
            masterHeartBeatTask.shutdown();
        }
        if (registryClient.isConnected()) {
            deregister();
        }
        log.info("封闭主注册表客户端");
    }

    /**
     * 注册当前Master节点（核心注册逻辑） 将当前主服务器本身注册到注册表。
     * 实现步骤：
     * 1. 检查当前服务状态（BUSY状态时循环等待）
     * 2. 清理旧注册数据（保证幂等性）
     * 3. 创建临时顺序节点（Ephemeral节点）
     * 4. 验证节点注册成功
     * 5. 启动心跳检测任务
     *
     * 关键技术点：
     * - 使用Zookeeper的Ephemeral节点特性实现故障自动发现
     * - 双重检查机制保证节点有效性
     * - 阻塞等待确保注册成功
     */
    void registry() {
        log.info("主节点 : {} 在注册中心注册", masterConfig.getMasterAddress());
        String masterRegistryPath = masterConfig.getMasterRegistryPath();

        // 状态检查循环（防止BUSY状态注册）
        MasterHeartBeat heartBeat = masterHeartBeatTask.getHeartBeat();
        while (ServerStatus.BUSY.equals(heartBeat.getServerStatus())) {
            log.warn("主节点忙: {}", heartBeat);
            heartBeat = masterHeartBeatTask.getHeartBeat();
            ThreadUtils.sleep(SLEEP_TIME_MILLIS);
        }

        // 先删除后创建（保证注册信息最新）
        registryClient.remove(masterRegistryPath);
        registryClient.persistEphemeral(masterRegistryPath, JSONUtils.toJsonString(masterHeartBeatTask.getHeartBeat()));

        // 注册结果验证循环（防止注册失败）
        while (!registryClient.checkNodeExists(NetUtils.getHost(), RegistryNodeType.MASTER)) {
            log.warn("当前主服务器节点:{} 在注册表中找不到", NetUtils.getHost());
            ThreadUtils.sleep(SLEEP_TIME_MILLIS);
        }

        //休眠1s，等待主故障转移删除旧的主节点 启动心跳任务（延迟启动确保注册完成）
        ThreadUtils.sleep(SLEEP_TIME_MILLIS);

        masterHeartBeatTask.start();
        log.info("主节点 : {} 已成功注册到注册中心", masterConfig.getMasterAddress());

    }

    /**
     * 注销节点方法（优雅关闭处理）
     * 执行步骤：
     * 1. 移除注册中心节点
     * 2. 停止心跳任务
     * 3. 关闭注册中心连接
     * 异常处理：捕获所有异常避免关闭流程中断
     */
    public void deregister() {
        try {
            registryClient.remove(masterConfig.getMasterRegistryPath());
            log.info("主节点 : {} 取消注册到注册中心.", masterConfig.getMasterAddress());
            if (masterHeartBeatTask != null) {
                masterHeartBeatTask.shutdown(); // 停止心跳
            }
            registryClient.close();// 关闭注册中心连接
        } catch (Exception e) {
            log.error("MasterServer删除注册表路径异常 ", e);
        }
    }

    /**
     * 服务可用性检查方法
     * @return 注册中心连接状态
     */
    public boolean isAvailable() {
        return registryClient.isConnected();
    }
}
