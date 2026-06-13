package com.xiaozhanke.deploy.monitor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 免 Agent 资源监控的装配与调度配置。
 *
 * <ul>
 *   <li>启用 {@link MonitorProperties} 绑定（{@code app.monitor.*}）；</li>
 *   <li>提供两个受限并发线程池——{@code monitorSamplingExecutor}（5s 资源采样）与
 *       {@code livenessProbeExecutor}（60s 在线检测），各自 core=max=并发上限、无界队列，
 *       使「同一周期内分批跑完全部主机、并发恒为上限」，且彼此隔离失败域、互不争用线程；</li>
 *   <li>以强类型 {@code Duration} 编程式注册两个 fixedDelay 任务（资源采样 + 在线检测），
 *       规避 {@code @Scheduled(fixedDelayString)} 占位符默认值与时长字符串解析的歧义。</li>
 * </ul>
 *
 * <p>{@code @EnableScheduling} 已由 {@code SchedulingConfig} 全局开启，故本 {@link SchedulingConfigurer}
 * 的 {@link #configureTasks} 会被调用。用 {@link ObjectProvider} 延迟获取采样器 / 在线检测器，
 * 打破「本配置 → 采样器 / 检测器 → 线程池 Bean → 本配置」的实例化环。
 *
 * @author xiaozhanke
 */
@Configuration
@EnableConfigurationProperties(MonitorProperties.class)
public class MonitoringConfig implements SchedulingConfigurer {

    private final MonitorProperties monitorProperties;
    private final ObjectProvider<HostMetricSampler> hostMetricSamplerProvider;
    private final ObjectProvider<LivenessProbeService> livenessProbeServiceProvider;

    public MonitoringConfig(MonitorProperties monitorProperties,
                            ObjectProvider<HostMetricSampler> hostMetricSamplerProvider,
                            ObjectProvider<LivenessProbeService> livenessProbeServiceProvider) {
        this.monitorProperties = monitorProperties;
        this.hostMetricSamplerProvider = hostMetricSamplerProvider;
        this.livenessProbeServiceProvider = livenessProbeServiceProvider;
    }

    /**
     * 采样线程池：core=max=采样并发上限，无界队列。core==max + 无界队列时并发恒为上限、多余主机排队，
     * 正是「并发设上限、在窗口内分批跑完」的语义。开启核心线程空闲回收，休眠期不常驻线程。
     */
    @Bean("monitorSamplingExecutor")
    public ThreadPoolTaskExecutor monitorSamplingExecutor() {
        return boundedMonitorExecutor("monitor-sample-");
    }

    /**
     * 在线检测线程池：与采样池同构（core=max=并发上限、无界队列、空闲回收），但<strong>独立</strong>，
     * 使 60s 在线检测与 5s 资源采样彼此隔离失败域、互不争用线程。
     */
    @Bean("livenessProbeExecutor")
    public ThreadPoolTaskExecutor livenessProbeExecutor() {
        return boundedMonitorExecutor("liveness-probe-");
    }

    /**
     * 监控唤醒线程池：仅承接订阅 0→正 / 正→0 时的「立即唤醒采样 / 整池拆光休眠」编排。
     *
     * <p>与采样池分离的两个理由：① 激活事件由 STOMP inbound 线程同步发布，编排含阻塞 SSH，必须挪离
     * inbound 线程，否则卡住整条实时通道；② 编排线程会 join 采样的 per-host 任务，若与之同池，
     * {@code samplingConcurrency=1} 时会自锁。单线程足够——唤醒与休眠本就经 {@code sampleLock} 串行。
     */
    @Bean("monitorWakeupExecutor")
    public ThreadPoolTaskExecutor monitorWakeupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("monitor-wakeup-");
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * 全局定时任务调度器（{@code @EnableScheduling} 默认仅单线程）。资源采样、在线检测两个 fixedDelay
     * 任务在触发线程上 join 等整轮跑完（主机多或离线时达数秒），与审计兜底回放共用单线程时会相互推迟。
     * 显式提供 3 线程，让这三个 fixedDelay 任务各占一线程、互不阻塞。Bean 名 {@code taskScheduler} 被
     * {@code ScheduledAnnotationBeanPostProcessor} 选作全局调度器，{@link #configureTasks} 注册的任务亦用它。
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("monitor-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 构造一个 core=max=采样并发上限、无界队列、核心线程空闲回收的监控线程池。
     */
    private ThreadPoolTaskExecutor boundedMonitorExecutor(String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int concurrency = monitorProperties.samplingConcurrency();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(
                () -> hostMetricSamplerProvider.getObject().scheduledSample(),
                monitorProperties.sampleInterval());
        taskRegistrar.addFixedDelayTask(
                () -> livenessProbeServiceProvider.getObject().scheduledProbe(),
                monitorProperties.livenessInterval());
    }
}
