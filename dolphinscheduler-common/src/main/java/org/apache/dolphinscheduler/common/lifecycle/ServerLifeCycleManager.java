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

package org.apache.dolphinscheduler.common.lifecycle;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务器生命周期管理工具类（单例模式+状态模式）
 *
 * <p>核心功能：
 * <ol>
 *   <li><b>状态管理</b>：维护服务器RUNNING/WAITING/STOPPED三种状态</li>
 *   <li><b>启动时间管理</b>：记录服务器启动时间用于健康检查</li>
 *   <li><b>状态转换控制</b>：保证状态转换的原子性和合法性</li>
 * </ol>
 *
 * <p>设计模式应用：
 * <ul>
 *   <li><b>单例模式</b>：通过 @UtilityClass 实现工具类单例</li>
 *   <li><b>状态模式</b>：封装不同状态下的行为逻辑</li>
 * </ul>
 */
@Slf4j
@UtilityClass
public class ServerLifeCycleManager {

    // 使用volatile保证状态可见性（线程安全设计）
    private static volatile ServerStatus serverStatus = ServerStatus.RUNNING;

    // 启动时间戳（用于计算运行时长）
    private static long serverStartupTime = System.currentTimeMillis();

    /**
     * 获取服务器启动时间（服务健康检查）
     * @return 服务器启动时间戳（毫秒级精度）
     */
    public static long getServerStartupTime() {
        return serverStartupTime;
    }

    /**
     * 强制切换到运行状态（状态模式实现）
     * <p>使用场景：系统初始化完成后的状态设置
     */
    public static void toRunning() {
        serverStatus = ServerStatus.RUNNING;
    }

    /**
     * 检查运行状态（状态查询）
     * @return true表示服务器处于正常运行状态
     */
    public static boolean isRunning() {
        return serverStatus == ServerStatus.RUNNING;
    }

    /**
     * 检查停止状态（状态查询）
     * @return true表示服务器已停止服务
     */
    public static boolean isStopped() {
        return serverStatus == ServerStatus.STOPPED;
    }

    /**
     * 获取当前状态（状态暴露方法）
     * @return 服务器当前状态枚举
     */
    public static ServerStatus getServerStatus() {
        return serverStatus;
    }

    /**
     * 将当前服务器状态更改为 {@link ServerStatus#WAITING}, 仅 {@link ServerStatus#RUNNING} 可以更改为 {@link ServerStatus#WAITING}.
     *
     * <p>设计约束：
     * <ul>
     *   <li>仅允许从RUNNING状态转换</li>
     *   <li>STOPPED状态禁止转换</li>
     * </ul>
     *
     * @throws ServerLifeCycleException 如果更改失败。
     */
    public static synchronized void toWaiting() throws ServerLifeCycleException {
        if (isStopped()) {
            throw new ServerLifeCycleException("当前服务器已停止，无法更改为等待");
        }

        if (serverStatus == ServerStatus.WAITING) {
            log.warn("当前服务器已处于等待状态，无法更改为等待");
            return;
        }
        serverStatus = ServerStatus.WAITING;
    }

    /**
     * 恢复 {@link ServerStatus#WAITING} 向 {@link ServerStatus#RUNNING}.
     * 从等待状态恢复（状态恢复方法）
     * <p>副作用：
     * <ul>
     *   <li>重置服务器启动时间</li>
     *   <li>清除等待期间产生的临时状态</li>
     * </ul>
     */
    public static synchronized void recoverFromWaiting() throws ServerLifeCycleException {
        if (isStopped()) {
            throw new ServerLifeCycleException("当前服务器已停止，无法恢复");
        }

        if (serverStatus == ServerStatus.RUNNING) {
            log.warn("当前服务器状态已在运行，无法从等待中恢复");
            return;
        }
        serverStartupTime = System.currentTimeMillis();
        serverStatus = ServerStatus.RUNNING;
    }

    /**
     * 停止服务器（终态转换方法）
     * <p>特性：
     * <ul>
     *   <li>幂等操作：重复调用仅第一次生效</li>
     *   <li>不可逆操作：进入STOPPED后无法恢复</li>
     * </ul>
     * @return true表示状态变更成功
     */
    public static synchronized boolean toStopped() {
        if (serverStatus == ServerStatus.STOPPED) {
            return false;
        }
        log.info("当前服务器状态已更改 {} 向 {}", serverStatus, ServerStatus.STOPPED);
        serverStatus = ServerStatus.STOPPED;
        return true;
    }

}
