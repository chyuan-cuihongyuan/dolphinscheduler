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

package org.apache.dolphinscheduler.extract.base.serialize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.dolphinscheduler.common.utils.JSONUtils;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static com.fasterxml.jackson.databind.DeserializationFeature.*;
import static com.fasterxml.jackson.databind.MapperFeature.REQUIRE_SETTERS_FOR_GETTERS;
import static org.apache.dolphinscheduler.common.constants.DateConstants.YYYY_MM_DD_HH_MM_SS;

/**
 * JSON序列化工具类（单例模式 + 工厂模式）
 *
 * <p>核心配置：
 * <ul>
 *   <li>忽略未知属性：FAIL_ON_UNKNOWN_PROPERTIES=false</li>
 *   <li>空数组转null：ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT=true</li>
 *   <li>未知枚举转null：READ_UNKNOWN_ENUM_VALUES_AS_NULL=true</li>
 *   <li>强制getter/setter配对：REQUIRE_SETTERS_FOR_GETTERS=true</li>
 *   <li>统一日期格式：yyyy-MM-dd HH:mm:ss</li>
 *   <li>LocalDateTime自定义序列化</li>
 * </ul>
 */
@Slf4j
public class JsonSerializer {

    /**
     * 线程安全的ObjectMapper实例（单例模式）
     * <p>配置特点：
     * <ol>
     *   <li>支持JDK8时间类型序列化</li>
     *   <li>使用系统默认时区</li>
     *   <li>宽松的序列化策略（适合RPC场景）</li>
     * </ol>
     */
    private static final ObjectMapper objectMapper = JsonMapper.builder()
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
            .configure(READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
            .configure(REQUIRE_SETTERS_FOR_GETTERS, true)
            .addModule(new SimpleModule()
                    .addSerializer(LocalDateTime.class, new JSONUtils.LocalDateTimeSerializer())
                    .addDeserializer(LocalDateTime.class, new JSONUtils.LocalDateTimeDeserializer()))
            .defaultTimeZone(TimeZone.getDefault())
            .defaultDateFormat(new SimpleDateFormat(YYYY_MM_DD_HH_MM_SS))
            .build();

    private JsonSerializer() {

    }

    /**
     * 序列化对象 → JSON字节数组（策略模式）
     * @param obj 支持类型：
     * <ul>
     *   <li>POJO对象（需符合JavaBean规范）</li>
     *   <li>集合类型（List/Map）</li>
     *   <li>基础类型（自动装箱）</li>
     * </ul>
     */
    public static <T> byte[] serialize(T obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            log.error("serializeToString exception!", e);
            return null;
        }
    }

    /**
     * 反序列化JSON字节数组 → 对象（模板方法模式）
     * @param src 必须为UTF-8编码字节数组
     * @param clazz 目标类型（不能是泛型类型）
     *
     * <p>异常处理：通过@SneakyThrows将检查异常转为非检查异常
     */
    @SneakyThrows
    public static <T> T deserialize(byte[] src, Class<T> clazz) {
        if (src == null) {
            return null;
        }

        String json = new String(src, StandardCharsets.UTF_8);
        return objectMapper.readValue(json, clazz);
    }

}
