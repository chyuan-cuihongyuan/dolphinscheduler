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

package org.apache.dolphinscheduler.extract.base;

import java.lang.annotation.*;
import java.net.ConnectException;

/**
 * 自定义注解：用于声明RPC方法的重试策略
 * <p>
 * 该注解可以应用于方法级别，用于指定当方法调用失败时的重试行为策略，
 * 包括最大重试次数、重试间隔时间和触发重试的异常类型。
 *
 * @Target(ElementType.METHOD)   限定该注解只能用于方法级别
 * @Retention(RetentionPolicy.RUNTIME) 注解在运行时可通过反射获取
 * @Documented 表明该注解应该包含在JavaDoc文档中
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcMethodRetryStrategy {

    /**
     * 最大重试次数配置
     * <p>
     * 包含首次方法调用，例如设置为3时，实际可能执行：首次调用 + 最多2次重试
     *
     * @return 最大方法执行次数（包含首次调用），默认值3次
     */
    int maxRetryTimes() default 3;

    /**
     * 重试间隔时间配置
     * <p>
     * 当值大于0时，每次重试前会等待指定时间间隔；小于等于0时不设置等待间隔
     *
     * @return 重试间隔时间（单位：毫秒），默认0表示无间隔
     */
    long retryInterval() default 0;

    /**
     * 触发重试的异常类型配置
     * <p>
     * 当方法抛出指定异常类型（或其子类）时，会触发重试机制。
     * 支持配置多个异常类型，当发生任一指定异常时都会进行重试。
     *
     * @return 需要触发重试的异常类型数组，默认包含ConnectException（连接异常）
     */
    Class<? extends Throwable>[] retryFor() default {ConnectException.class};

}
