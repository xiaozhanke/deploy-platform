package com.xiaozhanke.deploy.messaging.consumer;

import com.xiaozhanke.deploy.messaging.dto.ConfigChangeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 配置变更广播消费者(对应 MQ 方案稿场景 6)。
 *
 * <p><b>BROADCASTING 模式</b>:每条配置变更消息送达所有订阅实例——位移记录在消费者本地,
 * 而非 Broker。与部署作业 Topic 的 CLUSTERING(集群消费,实例间分摊)形成鲜明对比。
 *
 * <p>广播模式下<b>不支持消费失败重试</b>——失败即丢(或由应用层自行补偿)。本 consumer
 * 把变更写入日志,生产环境可扩展为刷新本地缓存 / 触发重载。
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${deploy-tool.mq.config-broadcast-topic:config-broadcast}",
        consumerGroup = "${deploy-tool.mq.config-broadcast-consumer-group:config-broadcast-consumer}",
        messageModel = MessageModel.BROADCASTING
)
public class ConfigBroadcastConsumer implements RocketMQListener<ConfigChangeMessage> {

    @Override
    public void onMessage(ConfigChangeMessage message) {
        log.info("收到配置变更广播: key=[{}] newValue=[{}] type=[{}] operator=[{}] time=[{}]",
                message.configKey(), message.newValue(), message.changeType(),
                message.operator(), message.changeTime());

        // 生产环境:根据 configKey 选择刷新策略
        // - SSH 超时相关 → 更新连接池参数
        // - 日志级别 → 动态调整 Logger
        // - 特性开关 → 刷新内存中缓存
        // 当前作品集阶段:写入 INFO 日志即可,面试时能讲清广播与集群消费的差异
        switch (message.changeType()) {
            case CREATED, UPDATED -> log.info("配置 [{}] 已刷新为 [{}]", message.configKey(), message.newValue());
            case DELETED -> log.info("配置 [{}] 已移除", message.configKey());
        }
    }
}
