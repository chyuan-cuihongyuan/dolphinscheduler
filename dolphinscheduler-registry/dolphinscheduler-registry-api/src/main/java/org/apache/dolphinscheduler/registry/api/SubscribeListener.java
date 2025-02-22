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

/**
 * 订阅监听器
 */
public interface SubscribeListener {

    /**
     * 通知
     *
     * @param event 活动
     */
    void notify(final Event event);

    /**
     * 获取订阅范围
     *
     * @return 订阅范围
     */
    SubscribeScope getSubscribeScope();

    /**
     * 订阅范围
     */
    enum SubscribeScope {
        /**
         * Only watch the path itself
         * 只通过路径进行观察
         */
        PATH_ONLY,
        /**
         * Only watch the children of the path
         * 只通过子路径进行观察
         */
        CHILDREN_ONLY,
        /**
         * Watch the path and all its children and the parent
         * 观察路径及其所有子路径和父路径
         */
        ALL

    }
}
