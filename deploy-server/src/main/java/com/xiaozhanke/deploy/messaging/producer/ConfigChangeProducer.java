package com.xiaozhanke.deploy.messaging.producer;

import com.xiaozhanke.deploy.messaging.dto.ConfigChangeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * 配置变更广播生产者(对应 MQ 方案稿场景 6)。
 *
 * <p>向 Topic 发送配置变更消息,由各实例的 {@code ConfigBroadcastConsumer}
 * 以 BROADCASTING 模式消费——每条消息送达所有订阅实例,实现配置实时同步。
 * 与部署作业 Topic 的 CLUSTERING(消费位移在 Broker,实例间分摊)形成对比演示。
 *
 * @author xiaozhanke
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigChangeProducer {

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${deploy-tool.mq.config-broadcast-topic:config-broadcast}")
    private String configBroadcastTopic;

    /**
     * 广播配置变更到所有实例。
     *
     * <p>MQ 发送失败绝不向上抛异常——配置广播是旁路通知,不能阻断主业务。
     * 失败仅记 warn 日志,不设兜底(与审计不同,广播丢失属于"尽力而为"语义)。
     *
     * @param message 配置变更消息体
     */
    public void broadcast(ConfigChangeMessage message) {
        try {
            SendResult result = rocketMQTemplate.syncSend(configBroadcastTopic,
                    MessageBuilder.withPayload(message).build());
            if (result != null && result.getSendStatus() == SendStatus.SEND_OK) {
                log.info("配置变更广播成功: key=[{}] type=[{}] operator=[{}]",
                        message.configKey(), message.changeType(), message.operator());
            } else {
                log.warn("配置变更广播状态异常: key=[{}] result=[{}]",
                        message.configKey(), result);
            }
        } catch (Exception e) {
            log.warn("配置变更广播发送失败: key=[{}] type=[{}] reason=[{}]",
                    message.configKey(), message.changeType(), e.getMessage());
        }
    }
}
