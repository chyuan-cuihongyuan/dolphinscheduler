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

package org.apache.dolphinscheduler.server.master.exception;

/**
 * 逻辑任务工厂未找到异常（关键配置异常）
 *
 * 核心职责：
 * 1. 标识任务插件体系中的工厂类加载失败问题
 * 2. 暴露任务类型与工厂类的映射关系异常
 * 3. 提示系统扩展机制配置缺陷
 *
 * 典型触发场景：
 * - 新增任务类型未注册对应工厂
 * - 任务类型与工厂映射配置错误
 * - 插件JAR包未正确加载
 * - 类路径下工厂实现类缺失
 *
 * 异常特征：
 * - 通常为系统启动阶段致命错误
 * - 需要人工干预修复配置
 * - 影响特定类型任务的调度能力
 */
public class LogicTaskFactoryNotFoundException extends MasterException {

    /**
     * 构造工厂缺失异常
     * @param message 必须包含以下要素：
     *                - 任务类型标识（如：SHELL、SQL等）
     *                - 预期工厂类全限定名
     *                - 配置位置信息
     *                示例："LogicTaskFactory for type [DATAX] not found, check spi configuration in task-plugin.properties"
     */
    public LogicTaskFactoryNotFoundException(String message) {
        super(message);
    }



    /**
     * （建议补充）带根本原因的构造器
     * @param message 技术摘要（如：ClassNotFoundException的详细信息）
     * @param cause 原始异常（如：ClassNotFoundException、NoSuchBeanDefinitionException）
     */
    public LogicTaskFactoryNotFoundException(String message, Throwable cause) {
        super(message, cause); // 当前代码未实现，建议添加
    }
}
