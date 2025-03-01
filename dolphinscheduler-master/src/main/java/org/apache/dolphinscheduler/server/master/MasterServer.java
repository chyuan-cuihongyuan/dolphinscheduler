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

package org.apache.dolphinscheduler.server.master;

import lombok.extern.slf4j.Slf4j;
import org.apache.dolphinscheduler.common.CommonConfiguration;
import org.apache.dolphinscheduler.common.IStoppable;
import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.common.lifecycle.ServerLifeCycleManager;
import org.apache.dolphinscheduler.common.thread.DefaultUncaughtExceptionHandler;
import org.apache.dolphinscheduler.common.thread.ThreadUtils;
import org.apache.dolphinscheduler.dao.DaoConfiguration;
import org.apache.dolphinscheduler.meter.metrics.MetricsProvider;
import org.apache.dolphinscheduler.meter.metrics.SystemMetrics;
import org.apache.dolphinscheduler.plugin.datasource.api.plugin.DataSourceProcessorProvider;
import org.apache.dolphinscheduler.plugin.storage.api.StorageConfiguration;
import org.apache.dolphinscheduler.plugin.task.api.TaskPluginManager;
import org.apache.dolphinscheduler.registry.api.RegistryConfiguration;
import org.apache.dolphinscheduler.scheduler.api.SchedulerApi;
import org.apache.dolphinscheduler.server.master.cluster.ClusterManager;
import org.apache.dolphinscheduler.server.master.cluster.ClusterStateMonitors;
import org.apache.dolphinscheduler.server.master.engine.MasterCoordinator;
import org.apache.dolphinscheduler.server.master.engine.WorkflowEngine;
import org.apache.dolphinscheduler.server.master.engine.system.SystemEventBus;
import org.apache.dolphinscheduler.server.master.engine.system.SystemEventBusFireWorker;
import org.apache.dolphinscheduler.server.master.engine.system.event.GlobalMasterFailoverEvent;
import org.apache.dolphinscheduler.server.master.metrics.MasterServerMetrics;
import org.apache.dolphinscheduler.server.master.registry.MasterRegistryClient;
import org.apache.dolphinscheduler.server.master.rpc.MasterRpcServer;
import org.apache.dolphinscheduler.server.master.utils.MasterThreadFactory;
import org.apache.dolphinscheduler.service.ServiceConfiguration;
import org.apache.dolphinscheduler.service.bean.SpringApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Date;

/**
 * DolphinScheduler 主节点服务器入口类，负责协调调度系统的核心服务生命周期。
 * <p>
 * 本类作为Master节点的总控中心，主要职责包括：
 * <ul>
 *   <li>系统服务组件的初始化与协调启动</li>
 *   <li>集群状态监控与故障转移处理</li>
 *   <li>工作流引擎的调度执行</li>
 *   <li>系统资源的指标采集与监控</li>
 *   <li>优雅停机与资源回收</li>
 * </ul>
 *
 * <p>核心设计特性：
 * <ul>
 *   <li>Spring Boot 应用主入口：通过@SpringBootApplication整合各模块配置</li>
 *   <li>生命周期管理：实现IStoppable接口保障有序启停</li>
 *   <li>事件驱动架构：通过SystemEventBus处理系统级事件</li>
 *   <li>容错机制：内置集群状态监控与自动故障恢复</li>
 * </ul>
 *
 * <p>
 * 本类作为系统控制中枢，整合了多种设计模式以实现高内聚低耦合的架构：
 *
 * <h3>核心设计模式应用</h3>
 *
 * <table>
 * <tr><th>设计模式</th><th>应用场景</th><th>代码体现</th></tr>
 * <tr><td>控制反转(IoC)</td><td>组件依赖管理</td><td>@Autowired自动注入/SpringApplicationContext</td></tr>
 * <tr><td>模板方法</td><td>标准化生命周期管理</td><td>initialized()方法中的固定启动流程</td></tr>
 * <tr><td>观察者模式</td><td>系统事件处理</td><td>SystemEventBus/GlobalMasterFailoverEvent</td></tr>
 * <tr><td>工厂方法</td><td>对象创建解耦</td><td>TaskPluginManager.loadTaskPlugin()</td></tr>
 * <tr><td>策略模式</td><td>集群管理策略</td><td>ClusterManager的多态实现</td></tr>
 * <tr><td>门面模式</td><td>复杂系统简化接口</td><td>SchedulerApi对外暴露统一API</td></tr>
 * <tr><td>装饰器模式</td><td>指标监控增强</td><td>MetricsProvider的指标包装</td></tr>
 * <tr><td>命令模式</td><td>停机流程封装</td><td>IStoppable接口实现</td></tr>
 * </table>
 *
 * <p>架构关键点解析：
 * <ul>
 *   <li>通过SpringBootApplication实现模块化配置加载(@Import)</li>
 *   <li>采用事件驱动架构处理集群故障转移等系统事件</li>
 *   <li>使用门面模式封装底层复杂的调度逻辑</li>
 *   <li>模板方法保证系统组件的标准启停顺序</li>
 * </ul>
 *
 * @see <a href="https://refactoring.guru/design-patterns">设计模式参考</a>
 * @see IStoppable 停机接口定义
 * @see WorkflowEngine 工作流执行引擎
 * @see ClusterManager 集群管理组件
 */

@Slf4j
@Import({
        DaoConfiguration.class,        // 数据访问对象配置（DAO层装配）
        ServiceConfiguration.class,    // 服务层配置（事务管理/服务Bean定义）
        CommonConfiguration.class,     // 通用工具配置（线程池/异常处理器）
        StorageConfiguration.class,    // 存储策略配置（HDFS/S3等实现）
        RegistryConfiguration.class    // 注册中心配置（ZK/Etcd等实现）
})
@SpringBootApplication
public class MasterServer implements IStoppable {

    // Spring应用上下文（用于依赖注入管理） 工厂方法模式
    @Autowired
    private SpringApplicationContext springApplicationContext;

    // 注册中心客户端（处理节点注册/发现） 观察者模式
    @Autowired
    private MasterRegistryClient masterRegistryClient;

    // 工作流执行引擎（核心调度逻辑） 策略模式
    @Autowired
    private WorkflowEngine workflowEngine;

    // 调度器API（对外暴露的调度服务）门面模式
    @Autowired
    private SchedulerApi schedulerApi;

    // RPC服务端（处理Worker节点通信） 装饰器模式
    @Autowired
    private MasterRpcServer masterRPCServer;

    // 指标采集器（系统性能监控）装饰器模式
    @Autowired
    private MetricsProvider metricsProvider;

    // 集群状态监控器（健康检查） 观察者模式
    @Autowired
    private ClusterStateMonitors clusterStateMonitors;

    // 集群管理器（节点管理）策略模式
    @Autowired
    private ClusterManager clusterManager;

    // 系统事件总线（处理全局事件） 发布-订阅模式
    @Autowired
    private SystemEventBus systemEventBus;

    // 事件总线工作线程（异步处理事件） 命令模式
    @Autowired
    private SystemEventBusFireWorker systemEventBusFireWorker;

    // Master协调器（分布式锁管理） 模板方法
    @Autowired
    private MasterCoordinator masterCoordinator;

    /**
     * 工厂方法模式入口（Spring Boot启动器）
     *
     * <p>初始化流程：
     * <ol>
     *   <li>注册全局异常处理器（装饰器模式增强）</li>
     *   <li>设置主线程标识（命名线程池模式）</li>
     *   <li>启动Spring容器（工厂方法创建Bean）</li>
     * </ol>
     */
    public static void main(String[] args) {
        // 注册指标采集（未捕获异常数） 装饰器模式：异常统计增强
        MasterServerMetrics.registerUncachedException(DefaultUncaughtExceptionHandler::getUncaughtExceptionCount);

        // 配置全局异常处理 策略模式：异常处理策略设置
        Thread.setDefaultUncaughtExceptionHandler(DefaultUncaughtExceptionHandler.getInstance());
        Thread.currentThread().setName(Constants.THREAD_NAME_MASTER_SERVER);

        // 启动Spring容器 工厂方法模式：创建Spring应用上下文
        SpringApplication.run(MasterServer.class);
    }

    /**
     * 模板方法模式：标准化初始化流程
     *
     * <p>启动顺序控制：
     * <pre>
     * 1. 状态标记（状态模式）
     * 2. RPC服务（网络层）
     * 3. 插件系统（工厂方法+SPI）
     * 4. 注册中心（观察者模式）
     * 5. 协调服务（模板方法）
     * 6. 集群管理（策略模式）
     * 7. 工作流引擎（状态模式）
     * 8. 调度API（门面模式）
     * 9. 事件系统（发布-订阅模式）
     * 10. 监控指标（装饰器模式）
     * </pre>
     */
    @PostConstruct
    public void initialized() {

        // 状态模式转换
        ServerLifeCycleManager.toRunning();
        final long startupTime = System.currentTimeMillis();

        // 初始化RPC服务（端口配置见master.rpc.port）观察者模式：启动RPC服务监听
        this.masterRPCServer.start();

        // 加载任务插件（SPI机制）工厂方法+SPI：加载任务插件
        TaskPluginManager.loadTaskPlugin();
        DataSourceProcessorProvider.initialize();

        // 启动注册中心客户端（ZK/Etcd） 观察者模式：注册中心启动
        this.masterRegistryClient.start();
        this.masterRegistryClient.setRegistryStoppable(this); // 策略模式注入

        // 启动分布式协调服务 模板方法：协调服务启动
        this.masterCoordinator.start();

        // 初始化集群管理组件 策略模式：集群管理启动
        this.clusterManager.start();
        this.clusterStateMonitors.start();

        // 启动工作流引擎（核心调度器） 状态模式：工作流引擎启动
        this.workflowEngine.start();

        // 暴露调度REST API 状态模式：门面模式：API服务启动
        this.schedulerApi.start();

        // 发布全局故障转移事件（处理冷启动场景） 发布-订阅模式：触发全局事件
        this.systemEventBus.publish(GlobalMasterFailoverEvent.of(new Date(startupTime)));
        this.systemEventBusFireWorker.start();

        /**
         * 注册系统级监控指标 装饰器模式：注册监控指标
         * <p>
         * 监控维度：
         * <ul>
         *   <li>CPU使用率：系统级CPU负载</li>
         *   <li>可用内存：物理内存剩余量（GB）</li>
         *   <li>JVM内存使用率：堆内存占比</li>
         * </ul>
         */
        // CPU使用率指标（百分比）
        MasterServerMetrics.registerMasterCpuUsageGauge(() -> {
            SystemMetrics systemMetrics = metricsProvider.getSystemMetrics();
            return systemMetrics.getSystemCpuUsagePercentage();
        });
        // 可用内存指标（单位：GB）
        MasterServerMetrics.registerMasterMemoryAvailableGauge(() -> {
            SystemMetrics systemMetrics = metricsProvider.getSystemMetrics();
            return (systemMetrics.getSystemMemoryMax() - systemMetrics.getSystemMemoryUsed()) / 1024.0 / 1024 / 1024;
        });
        // JVM内存使用率（百分比）
        MasterServerMetrics.registerMasterMemoryUsageGauge(() -> {
            SystemMetrics systemMetrics = metricsProvider.getSystemMetrics();
            return systemMetrics.getJvmMemoryUsedPercentage();
        });

        // 添加停机钩子（处理kill -15信号） 注册停机钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!ServerLifeCycleManager.isStopped()) {
                close("MasterServer关机挂钩");
            }
        }));
        log.info("MasterServer已在 {} 毫秒中成功初始化", System.currentTimeMillis() - startupTime);
    }

    /**
     * Spring容器销毁回调（优雅停机入口） 命令模式：标准停机接口实现
     */
    @PreDestroy
    public void shutdown() {
        close("主服务器关机");
    }

    /**
     * 系统关闭核心方法 资源管理模式：try-with-resources自动关闭
     * <p>
     * 停机流程：
     * <ol>
     *   <li>设置停机标志位</li>
     *   <li>等待3秒让业务线程完成</li>
     *   <li>关闭调度线程池</li>
     *   <li>按顺序关闭各组件（try-with-resources保证关闭顺序）</li>
     *      <p>资源管理模式：try-with-resources自动关闭
     *      <p>关闭顺序策略：
     *      <ul>
     *        <li>事件处理线程（观察者模式清理）</li>
     *        <li>工作流引擎（状态模式终止）</li>
     *        <li>调度API（门面模式关闭）</li>
     *        <li>RPC服务（网络层终止）</li>
     *        <li>协调服务（模板方法清理）</li>
     *        <li>注册中心（观察者模式注销）</li>
     *        <li>Spring上下文（工厂方法销毁）</li>
     *      </ul>
     *   <li>释放Spring上下文资源（工厂方法销毁）</li>
     * </ol>
     *
     * @param cause 关闭原因（用于日志追踪）
     */
    public void close(String cause) {
        // 设置停止信号为真，只执行一次
        // 状态检查（防止重复关闭）  状态模式检查
        if (!ServerLifeCycleManager.toStopped()) {
            log.warn("MasterServer已停止，当前原因: {}", cause);
            return;
        }

        // 等待业务线程结束（配置见server.close.wait.time）
        // 线程休眠3秒，线程安静停止 模板方法：等待线程终止
        ThreadUtils.sleep(Constants.SERVER_CLOSE_WAIT_TIME.toMillis());

        // 立即关闭调度线程池 命令模式：关闭线程池
        MasterThreadFactory.getDefaultSchedulerThreadExecutor().shutdownNow();

        // 资源释放（按依赖顺序关闭） try-with-resources模式：自动关闭资源
        try (
                SystemEventBusFireWorker systemEventBusFireWorker1 = systemEventBusFireWorker;
                WorkflowEngine workflowEngine1 = workflowEngine;
                SchedulerApi closedSchedulerApi = schedulerApi;
                MasterRpcServer closedRpcServer = masterRPCServer;
                MasterCoordinator closeMasterCoordinator = masterCoordinator;
                MasterRegistryClient closedMasterRegistryClient = masterRegistryClient;
                //关闭spring Context，并调用带有@PreDestroy注释的方法来销毁bean。
                //如服务器节点管理器、主机管理器、任务响应服务、管理员ZookeeperClient等
                SpringApplicationContext closedSpringContext = springApplicationContext) {

            log.info("MasterServer正在停止，当前原因: {}", cause);
        } catch (Exception e) {
            log.error("MasterServer停止失败，当前原因: {}", cause, e);
            return;
        }
        log.info("MasterServer已停止，当前原因：{}", cause);
    }

    /**
     * 强制停机接口实现（用于注册中心回调）
     * <p>
     * 与常规关闭的区别：
     * <ul>
     *   <li>直接调用System.exit确保进程终止</li>
     *   <li>处理zk session过期等场景</li>
     * </ul>
     *
     * @param cause 停机原因（如：心跳丢失）
     */
    @Override
    public void stop(String cause) {
        close(cause);

        //确保在服务器关闭后退出，不要在关闭逻辑中调用System.exit，如果关闭，将导致死锁
        //同时多次调用System.exit将导致JVM崩溃
        System.exit(1);  // 确保进程终止（避免僵尸进程）
    }
}
