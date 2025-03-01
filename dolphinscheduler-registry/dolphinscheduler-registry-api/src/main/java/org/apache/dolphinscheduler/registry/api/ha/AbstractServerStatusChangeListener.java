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

import lombok.extern.slf4j.Slf4j;

/**
 * 服务状态变更监听器基类（模板方法模式）
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>状态转换过滤</b>：处理ACTIVE/STAND_BY双向转换</li>
 *   <li><b>模板方法定义</b>：抽象具体状态处理逻辑</li>
 *   <li><b>扩展点封装</b>：子类只需关注目标状态</li>
 * </ol>
 *
 * <p>状态转换规则：
 * <ul>
 *   <li>ACTIVE → STAND_BY：触发changeToStandBy()</li>
 *   <li>STAND_BY → ACTIVE：触发changeToActive()</li>
 *   <li>其他状态转换：忽略</li>
 * </ul>
 */
@Slf4j
public abstract class AbstractServerStatusChangeListener implements ServerStatusChangeListener {

    /**
     * 状态变更事件处理（不可重写）
     * @param originStatus 原始状态（可能为null）
     * @param currentStatus 当前状态（非空）
     */
    @Override
    public void change(HAServer.ServerStatus originStatus, HAServer.ServerStatus currentStatus) {
        if (originStatus == HAServer.ServerStatus.ACTIVE) {
            if (currentStatus == HAServer.ServerStatus.STAND_BY) {
                changeToStandBy();
            }
        } else if (originStatus == HAServer.ServerStatus.STAND_BY) {
            if (currentStatus == HAServer.ServerStatus.ACTIVE) {
                changeToActive();
            }
        }
    }

    /**
     * 切换为ACTIVE状态回调（必须实现）
     * <p>典型操作：
     * <ul>
     *   <li>启动服务组件</li>
     *   <li>申请分布式资源</li>
     * </ul>
     */
    public abstract void changeToActive();

    /**
     * 切换为STAND_BY状态回调（必须实现）
     * <p>典型操作：
     * <ul>
     *   <li>释放占用的资源</li>
     *   <li>停止后台线程</li>
     * </ul>
     */
    public abstract void changeToStandBy();
}
