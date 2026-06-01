package com.xiaozhanke.deploy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务配置。
 *
 * <p>当前用于审计兜底文件回放({@code AuditFallbackReplayJob},MQ 方案稿场景 4)。
 *
 * @author xiaozhanke
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
