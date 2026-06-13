package com.xiaozhanke.deploy.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhanke.deploy.messaging.config.KafkaAuditProperties;
import com.xiaozhanke.deploy.messaging.dto.AuditLogMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 审计兜底文件读写器。
 *
 * <p>Kafka 不可用时,{@code AuditLogProducer} 把审计消息以 JSON 行追加到本地兜底文件,保证审计
 * "最终可观测"而<b>不</b>回滚业务;Kafka 恢复后由 {@code AuditFallbackReplayJob} 抽干文件批量回放。
 * 所有文件操作在同一把锁下串行,避免追加与抽干竞态丢行。解析(JSON ↔ DTO)放在锁外,缩短持锁时间。
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditFallbackWriter {

    private final KafkaAuditProperties properties;
    private final ObjectMapper objectMapper;
    private final Object fileLock = new Object();

    /**
     * 追加一条审计兜底消息(JSON 单行)。落盘失败说明本地磁盘也不可用,该条审计彻底丢失,记 error。
     */
    public void write(AuditLogMessage message) {
        String line;
        try {
            line = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.error("审计消息序列化失败,无法落兜底文件: {}", message, e);
            return;
        }
        synchronized (fileLock) {
            try {
                Path path = Path.of(properties.fallbackFile());
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.writeString(path, line + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.error("审计兜底文件写入失败,该条审计丢失: {}", message, e);
            }
        }
    }

    /**
     * 抽干兜底文件:读出全部行并删除文件(原子的"读 + 截断"),供回放任务处理。无文件时返回空列表。
     */
    public List<String> drainRawLines() {
        synchronized (fileLock) {
            Path path = Path.of(properties.fallbackFile());
            if (!Files.exists(path)) {
                return List.of();
            }
            try {
                List<String> lines = Files.readAllLines(path);
                Files.delete(path);
                List<String> nonBlank = new ArrayList<>();
                for (String line : lines) {
                    if (!line.isBlank()) {
                        nonBlank.add(line);
                    }
                }
                return nonBlank;
            } catch (IOException e) {
                log.error("读取审计兜底文件失败,本轮回放跳过", e);
                return List.of();
            }
        }
    }

    /**
     * 把回放仍失败的原始行追加回兜底文件,等待下一轮。
     */
    public void appendRawLines(List<String> rawLines) {
        if (rawLines.isEmpty()) {
            return;
        }
        synchronized (fileLock) {
            try {
                Path path = Path.of(properties.fallbackFile());
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.write(path, rawLines, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.error("回写审计兜底文件失败,{} 条审计可能丢失", rawLines.size(), e);
            }
        }
    }

    /**
     * 把原始 JSON 行解析回审计消息;解析失败(脏行)返回 null,由调用方丢弃。
     */
    public AuditLogMessage parse(String rawLine) {
        try {
            return objectMapper.readValue(rawLine, AuditLogMessage.class);
        } catch (IOException e) {
            log.warn("审计兜底文件存在无法解析的脏行,丢弃: {}", rawLine, e);
            return null;
        }
    }
}
