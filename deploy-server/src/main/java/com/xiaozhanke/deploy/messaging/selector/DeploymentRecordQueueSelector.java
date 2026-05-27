package com.xiaozhanke.deploy.messaging.selector;

import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按 deploymentRecordId 选队列的顺序消息选择器(详见 ADR-0001)。
 *
 * <p>同一份部署记录的所有作业进同一队列 → 串行消费;不同部署记录(包括同机不同应用)的作业
 * 分散到不同队列 → 并发消费。Phase 1 中事务消息走默认队列选择,本选择器为场景 2 的顺序消息
 * 单独路径预留(通过 {@code syncSendOrderly} 调用)。
 *
 * @author xiaozhanke
 */
@Component
public class DeploymentRecordQueueSelector implements MessageQueueSelector {

    /**
     * @param mqs broker 暴露的 MessageQueue 列表
     * @param msg 当前要发送的消息
     * @param arg 顺序键(本项目固定为 {@code deploymentRecordId} 字符串)
     */
    @Override
    public MessageQueue select(List<MessageQueue> mqs, Message msg, Object arg) {
        if (arg == null) {
            throw new IllegalArgumentException("ordering key (deploymentRecordId) must not be null");
        }
        String deploymentRecordId = arg.toString();
        // floorMod 避免负 hashCode 取模为负
        int index = Math.floorMod(deploymentRecordId.hashCode(), mqs.size());
        return mqs.get(index);
    }
}
