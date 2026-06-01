package com.xiaozhanke.deploy.audit;

import com.xiaozhanke.deploy.messaging.config.KafkaAuditProperties;
import com.xiaozhanke.deploy.messaging.dto.AuditLogMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 审计兜底文件回放任务(对应 MQ 方案稿场景 4、ADR-0005)。
 *
 * <p>定时抽干兜底文件并尝试重新发往 Kafka:成功的丢弃,仍失败的(Kafka 未恢复)原样回写等下一轮。
 * 与 {@code AuditFallbackWriter} 共用同一把文件锁,抽干是原子的"读 + 删",新写入不会丢。
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditFallbackReplayJob {

    private final AuditFallbackWriter fallbackWriter;
    private final KafkaTemplate<String, AuditLogMessage> auditKafkaTemplate;
    private final KafkaAuditProperties properties;

    /**
     * 周期回放(默认 60s 一次,可配 {@code deploy-tool.audit.replay-interval-millis})。
     */
    @Scheduled(fixedDelayString = "${deploy-tool.audit.replay-interval-millis:60000}")
    public void replay() {
        List<String> rawLines = fallbackWriter.drainRawLines();
        if (rawLines.isEmpty()) {
            return;
        }
        log.info("开始回放审计兜底文件,共 {} 条", rawLines.size());

        List<String> stillFailed = new ArrayList<>();
        int replayed = 0;
        for (int i = 0; i < rawLines.size(); i++) {
            String raw = rawLines.get(i);
            AuditLogMessage message = fallbackWriter.parse(raw);
            if (message == null) {
                // 脏行:解析失败,丢弃不回写
                continue;
            }
            try {
                auditKafkaTemplate.send(properties.topic(), message)
                        .get(properties.sendTimeoutMillis(), TimeUnit.MILLISECONDS);
                replayed++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // 中断:把当前及之后所有未处理行整体回写,避免丢失
                for (int j = i; j < rawLines.size(); j++) {
                    stillFailed.add(rawLines.get(j));
                }
                break;
            } catch (Exception e) {
                // Kafka 仍不可用:留到下一轮
                stillFailed.add(raw);
            }
        }
        fallbackWriter.appendRawLines(stillFailed);
        log.info("审计兜底回放完成:成功 {} 条,仍失败 {} 条", replayed, stillFailed.size());
    }
}
