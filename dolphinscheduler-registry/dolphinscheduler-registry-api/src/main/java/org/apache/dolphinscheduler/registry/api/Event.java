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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 事件
 */
@Getter
@ToString
@Builder
@AllArgsConstructor
public class Event {

    // The path which is watched
    // 被监视的路径
    private final String watchedPath;
    // The full path where the event was generated
    // 事件生成的完整路径
    private final String eventPath;
    // The value corresponding to the path
    // 与路径对应的值
    private final String eventData;
    // The event type {ADD, REMOVE, UPDATE}
    // 事件类型｛ADD、REMOVE、UPDATE｝
    private Type type;

    /**
     * 事件类型
     */
    public enum Type {
        ADD, // 添加
        REMOVE, // 移除
        UPDATE // 更新
    }

}
