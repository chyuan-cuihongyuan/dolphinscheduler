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

package org.apache.dolphinscheduler.dao.utils;

import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.dao.entity.WorkerGroup;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;

/**
 * 工作组工具类
 */
public class WorkerGroupUtils {

    // 默认工作组
    private static final String DEFAULT_WORKER_GROUP = "default";

    /**
     * Check if the worker group is empty, if the worker group is default, it is considered empty
     * <P>（检查工作组是否为空，如果工作组为默认值，则视为空）
     *
     * @param workerGroup 工作组
     * @return 是否为空
     */
    public static boolean isWorkerGroupEmpty(String workerGroup) {
        return StringUtils.isEmpty(workerGroup) || getDefaultWorkerGroup().equals(workerGroup);
    }

    /**
     * 获取工作组或默认值
     *
     * @param workerGroup 工作组
     * @return 工作组或默认值
     */
    public static String getWorkerGroupOrDefault(String workerGroup) {
        return getWorkerGroupOrDefault(workerGroup, getDefaultWorkerGroup());
    }

    /**
     * 获取工作组或默认值
     *
     * @param workerGroup        工作组
     * @param defaultWorkerGroup 默认工作组
     * @return 工作组或默认值
     */
    public static String getWorkerGroupOrDefault(String workerGroup, String defaultWorkerGroup) {
        return isWorkerGroupEmpty(workerGroup) ? defaultWorkerGroup : workerGroup;
    }

    /**
     * 获取默认工作组
     *
     * @return 默认工作组
     */
    public static String getDefaultWorkerGroup() {
        return DEFAULT_WORKER_GROUP;
    }

    /**
     * 从工作组获取工作地址列表
     *
     * @param workerGroup 工作组
     * @return 工作地址列表
     */
    public static List<String> getWorkerAddressListFromWorkerGroup(WorkerGroup workerGroup) {
        String addrList = workerGroup.getAddrList();
        if (StringUtils.isEmpty(addrList)) {
            return Collections.emptyList();
        }
        return Lists.newArrayList(addrList.split(Constants.COMMA));
    }

}
