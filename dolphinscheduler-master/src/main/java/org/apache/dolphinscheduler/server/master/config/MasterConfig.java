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

import org.apache.dolphinscheduler.common.utils.NetUtils;
import org.apache.dolphinscheduler.registry.api.enums.RegistryNodeType;
import org.apache.dolphinscheduler.server.master.cluster.loadbalancer.WorkerLoadBalancerConfigurationProperties;

import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

/**
 * Master服务器配置类，用于加载以'master'为前缀的配置项，并实现配置参数的校验逻辑。
 * <p>包含以下核心配置项：</p>
 * <ul>
 *   <li>RPC服务监听端口配置</li>
 *   <li>工作流事件总线线程池配置</li>
 *   <li>心跳检测间隔配置</li>
 *   <li>Worker组刷新策略配置</li>
 *   <li>服务负载保护机制配置</li>
 *   <li>Worker负载均衡策略配置</li>
 * </ul>
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "master")
@Slf4j
public class MasterConfig implements Validator {

    /**
     * The master RPC server listen port. 主RPC服务器侦听端口。
     * RPC服务监听端口（默认：5678）
     */
    private int listenPort = 5678;

    /**
     * 工作流事件总线处理线程数（默认值：CPU核心数*2+1）
     */
    private int workflowEventBusFireThreadCount = Runtime.getRuntime().availableProcessors() * 2 + 1;

    /**
     * 逻辑任务执行配置（包含任务提交/执行相关参数）
     */
    private LogicTaskConfig logicTaskConfig = new LogicTaskConfig();

    /**
     * Master heart beat task execute interval. 掌握心跳任务执行间隔。
     * 最大心跳检测间隔（默认：10秒）
     */
    private Duration maxHeartbeatInterval = Duration.ofSeconds(10);

    /**
     * 服务端负载保护配置（包含CPU/内存阈值等保护机制）
     */
    private MasterServerLoadProtection serverLoadProtection = new MasterServerLoadProtection();

    /**
     * Worker组信息刷新间隔（默认：5分钟）
     */
    private Duration workerGroupRefreshInterval = Duration.ofMinutes(5);

    /**
     * 命令抓取策略配置（控制命令获取频率和批量大小）
     */
    private CommandFetchStrategy commandFetchStrategy = new CommandFetchStrategy();

    /**
     * Worker负载均衡器配置（包含负载计算算法和权重参数）
     */
    private WorkerLoadBalancerConfigurationProperties workerLoadBalancerConfigurationProperties =
            new WorkerLoadBalancerConfigurationProperties();

    /**
     * The IP address and listening port of the master server in the format 'ip:listenPort'.
     * 主服务器的IP地址和侦听端口，格式为“IP:listenPort”。
     * Master服务地址
     */
    private String masterAddress;

    /**
     * The registry path for the master server in the format '/nodes/master/ip:listenPort'.
     * 主服务器的注册表路径，格式为“nodesmasterip:listenPort”。
     * 注册中心路径
     */
    private String masterRegistryPath;

    /**
     * 配置校验入口方法，执行以下校验逻辑：
     * <ul>
     *   <li>监听端口有效性检查</li>
     *   <li>事件总线线程数正数校验</li>
     *   <li>心跳间隔有效性检查</li>
     *   <li>Worker组刷新间隔下限检查（>=10秒）</li>
     *   <li>自动生成Master地址（当未配置时）</li>
     *   <li>校验命令抓取策略参数</li>
     *   <li>校验负载均衡器参数</li>
     * </ul>
     */
    @Override
    public boolean supports(Class<?> clazz) {
        return MasterConfig.class.isAssignableFrom(clazz);
    }

    /**
     * 打印完整配置信息到日志（DEBUG级别）
     * <p>输出格式包含星号分隔的配置块，包含所有关键配置参数</p>
     */
    @Override
    public void validate(Object target, Errors errors) {
        MasterConfig masterConfig = (MasterConfig) target;
        if (masterConfig.getListenPort() <= 0) {
            errors.rejectValue("listen-port", null, "is invalidated");
        }

        if (masterConfig.getWorkflowEventBusFireThreadCount() <= 0) {
            errors.rejectValue("workflow-event-bus-fire-thread-count", null, "should be a positive value");
        }

        if (masterConfig.getMaxHeartbeatInterval().toMillis() < 0) {
            errors.rejectValue("max-heartbeat-interval", null, "should be a valid duration");
        }

        if (masterConfig.getWorkerGroupRefreshInterval().getSeconds() < 10) {
            errors.rejectValue("worker-group-refresh-interval", null, "should >= 10s");
        }
        if (StringUtils.isEmpty(masterConfig.getMasterAddress())) {
            masterConfig.setMasterAddress(NetUtils.getAddr(masterConfig.getListenPort()));
        }
        commandFetchStrategy.validate(errors);
        workerLoadBalancerConfigurationProperties.validate(errors);

        masterConfig.setMasterRegistryPath(
                RegistryNodeType.MASTER.getRegistryPath() + "/" + masterConfig.getMasterAddress());
        printConfig();
    }

    private void printConfig() {
        String config =
                "\n****************************Master Configuration**************************************" +
                        "\n  listen-port -> " + listenPort +
                        "\n  workflow-event-bus-fire-thread-count -> " + workflowEventBusFireThreadCount +
                        "\n  logic-task-config -> " + logicTaskConfig +
                        "\n  max-heartbeat-interval -> " + maxHeartbeatInterval +
                        "\n  server-load-protection -> " + serverLoadProtection +
                        "\n  master-address -> " + masterAddress +
                        "\n  master-registry-path: " + masterRegistryPath +
                        "\n  worker-group-refresh-interval: " + workerGroupRefreshInterval +
                        "\n  command-fetch-strategy: " + commandFetchStrategy +
                        "\n  worker-load-balancer-configuration-properties: "
                        + workerLoadBalancerConfigurationProperties +
                        "\n****************************Master Configuration**************************************";
        log.info(config);
    }
}
