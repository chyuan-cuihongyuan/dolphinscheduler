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

package org.apache.dolphinscheduler.server.master.config;

import org.apache.dolphinscheduler.meter.metrics.BaseServerLoadProtection;

import lombok.extern.slf4j.Slf4j;

/**
 * Master 服务负载保护配置
 * <p>继承基础负载保护能力，用于管理 Master 服务的系统资源保护策略
 *
 * <p>当前版本直接复用基类实现，保留扩展能力用于未来可能的 Master 定制化保护策略
 */
@Slf4j
public class MasterServerLoadProtection extends BaseServerLoadProtection {
    // 保留空类结构用于后续扩展
}
