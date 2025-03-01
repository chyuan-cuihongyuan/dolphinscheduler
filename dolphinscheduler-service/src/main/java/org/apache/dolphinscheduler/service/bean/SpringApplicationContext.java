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

package org.apache.dolphinscheduler.service.bean;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class SpringApplicationContext implements ApplicationContextAware, AutoCloseable {

    // 静态持有Spring应用上下文（单例模式实现）
    // 作用：提供全局访问点的设计模式，确保整个应用中只存在一个ApplicationContext实例
    // 优点：简化了对Spring容器的访问，提供了一种全局访问Spring容器中Bean的方式
    // 缺点：可能导致内存泄漏，因为ApplicationContext是一个单例对象，它的生命周期与整个应用程序相同，
    // 如果没有正确地关闭它，可能会导致内存泄漏
    private static ApplicationContext applicationContext;

    /**
     * 实现ApplicationContextAware接口的方法（回调模式）
     * 功能：当Spring容器初始化时，自动将应用上下文注入到当前类中
     * @param applicationContext Spring应用上下文对象
     * @throws BeansException 如果上下文注入失败时抛出
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringApplicationContext.applicationContext = applicationContext;
    }

    /**
     * 实现AutoCloseable接口的方法（资源管理模式）
     * 功能：关闭应用上下文时执行资源清理
     * 设计模式：模板方法模式，通过调用AbstractApplicationContext的标准关闭流程
     * 效果：会触发bean的destroy方法，释放数据库连接池等资源
     */
    @Override
    public void close() {
        ((AbstractApplicationContext) applicationContext).close();
    }

    /**
     * 静态工厂方法（工厂模式）
     * 功能：根据类型获取Spring容器中的Bean实例
     * @param requiredType 需要获取的Bean类型
     * @return 匹配的Bean实例
     * @throws NoSuchBeanDefinitionException 当找不到对应Bean时抛出
     */
    public static <T> T getBean(Class<T> requiredType) {
        return applicationContext.getBean(requiredType);
    }

    /**
     * 增强版Bean获取方法（安全封装模式）
     * 功能：在找不到Bean时返回默认值，避免抛出异常
     * 设计特点：
     * 1. 使用防御式编程思想
     * 2. 异常处理机制
     * 3. 默认值策略模式
     * @param requiredType 需要获取的Bean类型
     * @param defaultValue 默认返回值
     * @return Bean实例或默认值
     */
    public static <T> T getBean(Class<T> requiredType, T defaultValue) {
        try {
            return applicationContext.getBean(requiredType);
        } catch (NoSuchBeanDefinitionException e) {
            return defaultValue;
        }
    }
}
