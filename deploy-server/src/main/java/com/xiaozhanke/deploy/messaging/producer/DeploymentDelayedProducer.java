package com.xiaozhanke.deploy.messaging.producer;

import com.xiaozhanke.deploy.messaging.dto.DelayedJobMessage;
import com.xiaozhanke.deploy.model.entity.DeploymentJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 延迟部署作业生产者(对应 MQ 方案稿场景 3、ADR-0004)。
 *
 * <p>负责把延迟作业消息发到专门 Topic,选 RocketMQ 内置 delayLevel。对于超过 RocketMQ
 * 内置最大延迟(2h)的长延迟,本类只发首段链节;后续接力由 {@code DeploymentDelayedConsumer}
 * 在消费端自发完成(详见 ADR-0004 的"短延迟+重发接力链")。
 *
 * <p>RocketMQ 18 级延迟(1s/5s/10s/30s/1m/2m/3m/4m/5m/6m/7m/8m/9m/10m/20m/30m/1h/2h),
 * 对应 delayLevel 1~18。
 *
 * @author xiaozhanke
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentDelayedProducer {

    /**
     * 单段接力链最大持续时间(秒):2 小时,对应 RocketMQ 内置最大 delayLevel
     */
    public static final long MAX_SINGLE_HOP_SECONDS = 2 * 60 * 60L;
    // RocketMQ 18 级延迟秒数映射表(delayLevel 1~18,索引 0 占位)
    private static final long[] LEVEL_SECONDS = {
            0, 1, 5, 10, 30, 60, 120, 180, 240, 300, 360, 420, 480, 540, 600, 1200, 1800, 3600, 7200};
    private final RocketMQTemplate rocketMQTemplate;
    @Value("${deploy-platform.mq.delayed-topic:deploy-job-delayed}")
    private String delayedTopic;

    /**
     * 把延迟秒数映射到 RocketMQ delayLevel(1~18)。
     *
     * <p>18 级延迟:1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h。
     * 算法:找到 ≤ targetSeconds 的最大级别(不超发——延迟可以稍短,但不能比用户要求的晚)。
     */
    static int toDelayLevel(long targetSeconds) {
        int bestLevel = 1;
        for (int level = 18; level >= 1; level--) {
            if (LEVEL_SECONDS[level] <= targetSeconds) {
                bestLevel = level;
                break;
            }
        }
        return bestLevel;
    }

    /**
     * 发送延迟作业消息(首段链节)。
     *
     * <p>根据 {@code delaySeconds} 映射到最接近的 RocketMQ delayLevel;若超过 2h,
     * 首段只发 2h,由 consumer 续发后续链节。
     *
     * @param pendingJob   已入库的 PENDING 作业
     * @param executeAt    用户期望执行时间
     * @param delaySeconds 距现在的延迟秒数
     * @return 发送结果
     */
    public SendResult sendDelayed(DeploymentJob pendingJob, LocalDateTime executeAt, long delaySeconds) {
        long firstHopSeconds = Math.min(delaySeconds, MAX_SINGLE_HOP_SECONDS);
        long remainingAfterHop = delaySeconds - firstHopSeconds;

        DelayedJobMessage payload = new DelayedJobMessage(
                pendingJob.getId(),
                pendingJob.getDeploymentRecord().getId(),
                pendingJob.getJobType(),
                pendingJob.getClientRequestId(),
                executeAt,
                remainingAfterHop,
                0, // 首段 hop=0
                LocalDateTime.now());

        SendResult result = sendMessage(payload, toDelayLevel(firstHopSeconds));
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            log.warn("延迟消息发送状态非 SEND_OK: jobId=[{}] delayLevel=[{}] result=[{}]",
                    pendingJob.getId(), toDelayLevel(firstHopSeconds), result);
        } else {
            log.info("延迟消息发送成功: jobId=[{}] delay=[{}s] delayLevel=[{}] 剩余=[{}s] msgId=[{}]",
                    pendingJob.getId(), firstHopSeconds, toDelayLevel(firstHopSeconds),
                    remainingAfterHop, result.getMsgId());
        }
        return result;
    }

    /**
     * 续发下段接力链节(由 consumer 调用)。
     *
     * <p>返回值非 SEND_OK 或 null 时抛异常,由 consumer 的 try-catch 兜底转死信,
     * 避免接力链静默断裂。
     *
     * @param payload             当前已收到的消息体(含剩余延迟)
     * @param nextHopDelaySeconds 下一段延迟秒数
     * @throws RuntimeException 发送失败时抛出,由 consumer 捕获并转死信
     */
    public void sendNextHop(DelayedJobMessage payload, long nextHopDelaySeconds) {
        long nextRemaining = payload.remainingDelaySeconds() - nextHopDelaySeconds;
        DelayedJobMessage nextPayload = new DelayedJobMessage(
                payload.jobId(), payload.deploymentRecordId(), payload.jobType(),
                payload.clientRequestId(), payload.executeAt(), nextRemaining,
                payload.relayHop() + 1, LocalDateTime.now());

        SendResult result = sendMessage(nextPayload, toDelayLevel(nextHopDelaySeconds));
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            String msg = String.format("续发延迟链节失败: jobId=[%s] hop=[%d] status=[%s]",
                    payload.jobId(), nextPayload.relayHop(),
                    result == null ? "null" : result.getSendStatus());
            log.error(msg);
            throw new RuntimeException(msg);
        }
        log.info("续发延迟链节成功: jobId=[{}] hop=[{}] delay=[{}s] 剩余=[{}s] msgId=[{}]",
                payload.jobId(), nextPayload.relayHop(), nextHopDelaySeconds,
                nextRemaining, result.getMsgId());
    }

    /**
     * 构建消息并发送到延迟 Topic。
     */
    private SendResult sendMessage(DelayedJobMessage payload, int delayLevel) {
        Message<DelayedJobMessage> message = MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, payload.jobId())
                .setHeader("jobId", payload.jobId())
                .build();
        return rocketMQTemplate.syncSend(delayedTopic, message,
                rocketMQTemplate.getProducer().getSendMsgTimeout(), delayLevel);
    }
}
