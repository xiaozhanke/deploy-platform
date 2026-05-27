package com.xiaozhanke.deploy.messaging.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 模块配置入口
 *
 * <p>rocketmq-spring-boot-starter 已经自动注入 {@code RocketMQTemplate},本类只负责把
 * 业务配置(见 {@link RocketMQProperties})绑定到 Spring 上下文。
 *
 * @author xiaozhanke
 */
@Configuration
@EnableConfigurationProperties(RocketMQProperties.class)
public class RocketMQConfig {
}
