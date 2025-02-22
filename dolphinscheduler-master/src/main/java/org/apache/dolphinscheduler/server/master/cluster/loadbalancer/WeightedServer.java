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

package org.apache.dolphinscheduler.server.master.cluster.loadbalancer;

import org.apache.dolphinscheduler.server.master.cluster.IClusters;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 加权服务器
 *
 * @param <T> 服务器
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WeightedServer<T extends IClusters.IServerMetadata> {

    // 服务器
    private T server;

    // 权重
    private double weight;

    // 当前权重
    private double currentWeight;

    public WeightedServer(T server, double weight) {
        this.server = server;
        this.weight = weight;
        this.currentWeight = 0; // Initialize currentWeight is 0 初始化当前权重为0
    }

}
