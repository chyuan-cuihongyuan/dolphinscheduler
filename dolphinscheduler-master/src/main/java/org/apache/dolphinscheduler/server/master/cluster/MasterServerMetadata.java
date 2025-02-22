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

package org.apache.dolphinscheduler.server.master.cluster;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.common.model.MasterHeartBeat;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 主服务器元数据
 */
@Data
@ToString(callSuper = true)
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MasterServerMetadata extends BaseServerMetadata implements Comparable<MasterServerMetadata> {

    /**
     * 从心跳数据包解析生成主服务器元数据对象
     *
     * @param masterHeartBeat 主服务器心跳数据包，包含进程ID、启动时间等元数据信息
     * @return 构建完成的主服务器元数据对象，包含地址拼接、资源使用率等派生字段
     * @throws NullPointerException 当输入参数为null时抛出异常
     */
    public static MasterServerMetadata parseFromHeartBeat(final MasterHeartBeat masterHeartBeat) {
        checkNotNull(masterHeartBeat);
        // 通过Builder模式构造对象，转换心跳数据到元数据结构
        return MasterServerMetadata.builder()
                .processId(masterHeartBeat.getProcessId())
                .serverStartupTime(masterHeartBeat.getStartupTime())
                .address(masterHeartBeat.getHost() + Constants.COLON + masterHeartBeat.getPort())
                .cpuUsage(masterHeartBeat.getCpuUsage())
                .memoryUsage(masterHeartBeat.getMemoryUsage())
                .serverStatus(masterHeartBeat.getServerStatus())
                .build();
    }

    /**
     * 实现主服务器元数据排序比较逻辑
     *
     * @param o 被比较的主服务器元数据对象
     * @return 正数表示当前对象地址更大，0表示地址相同，负数表示更小
     *         按地址字符串的自然字典序进行排序
     */
    @Override
    public int compareTo(final MasterServerMetadata o) {
        return this.getAddress().compareTo(o.getAddress());
    }

}
