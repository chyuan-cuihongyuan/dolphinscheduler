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

/**
 * 标识一个方法作为远程过程调用（RPC）方法的注解。
 * <p>
 * 该注解应用于方法级别，用于定义RPC调用相关的配置参数，包括超时时间和重试策略。
 * 运行时保留此注解，可通过反射机制读取配置信息。
 *
 * @Target(ElementType.METHOD)  表示该注解仅可用于方法声明
 * @Retention(RetentionPolicy.RUNTIME)  表示注解在运行时可通过反射获取
 * @Documented  表示该注解应包含在Javadoc文档中
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcMethod {

    /**
     * 定义RPC调用的超时时间（单位：毫秒）
     * <p>
     * 默认值-1表示不单独设置超时，遵循系统全局默认超时配置。
     * 设置为正整数时，将覆盖全局配置作用于当前方法。
     */
    long timeout() default -1;


    /**
     * 定义RPC调用失败时的重试策略配置
     * <p>
     * 通过嵌套注解{@link RpcMethodRetryStrategy}指定具体重试规则，
     * 包括重试次数、重试条件等。默认创建空配置对象时将采用策略接口的默认实现逻辑。
     */
    RpcMethodRetryStrategy retry() default @RpcMethodRetryStrategy;

}
