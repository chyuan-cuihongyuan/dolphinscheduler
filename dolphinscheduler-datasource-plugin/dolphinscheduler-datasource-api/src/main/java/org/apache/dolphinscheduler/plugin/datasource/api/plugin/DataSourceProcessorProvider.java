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

package org.apache.dolphinscheduler.plugin.datasource.api.plugin;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.dolphinscheduler.plugin.datasource.api.datasource.DataSourceProcessor;
import org.apache.dolphinscheduler.spi.enums.DbType;

import java.util.Map;

/**
 * 数据源处理器提供者（工厂模式 + SPI机制）
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>处理器注册</b>：通过静态块自动加载SPI实现</li>
 *   <li><b>统一访问入口</b>：提供静态方法获取数据源处理器</li>
 *   <li><b>类型安全转换</b>：将DbType映射为具体处理器实现</li>
 * </ol>
 *
 * <p>关键设计：
 * <ul>
 *   <li>单例模式：私有构造器防止实例化</li>
 *   <li>静态初始化：类加载时自动注册处理器</li>
 *   <li>类型映射：DbType枚举与处理器名称绑定</li>
 * </ul>
 */
@Slf4j
public class DataSourceProcessorProvider {
    // 处理器管理中心（持有所有数据源处理器）
    private static final DataSourceProcessorManager dataSourcePluginManager = new DataSourceProcessorManager();

    // 类加载时自动初始化（SPI机制加载处理器）
    static {
        dataSourcePluginManager.installProcessor(); // 加载META-INF/services下的处理器
    }

    // 隐藏构造器（工具类设计）
    private DataSourceProcessorProvider() {
    }

    public static void initialize() {
        log.info("Initialize DataSourceProcessorProvider");
    }

    /**
     * 获取指定类型的数据源处理器
     * @param dbType 数据库类型枚举
     * @return 处理器实例（可能返回null）
     *
     * <p>映射逻辑示例：
     * DbType.MYSQL → 查找"MySQL"对应的DataSourceProcessor
     */
    public static DataSourceProcessor getDataSourceProcessor(@NonNull DbType dbType) {
        return dataSourcePluginManager.getDataSourceProcessorMap().get(dbType.name());
    }

    /**
     * 获取全量处理器映射
     * @return 不可修改的处理器集合（Key为dbType.name()）
     *
     * <p>典型使用场景：
     * <ul>
     *   <li>校验支持的数据源类型</li>
     *   <li>批量操作处理器</li>
     * </ul>
     */
    public static Map<String, DataSourceProcessor> getDataSourceProcessorMap() {
        return dataSourcePluginManager.getDataSourceProcessorMap();
    }

}
