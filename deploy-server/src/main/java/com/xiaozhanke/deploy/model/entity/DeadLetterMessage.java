package com.xiaozhanke.deploy.model.entity;

import com.xiaozhanke.deploy.enums.JobTypeEnum;
import com.xiaozhanke.deploy.model.base.BasePo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * 死信消息 PO 类(对应 MQ 方案稿场景 5、ADR-0003)。
 *
 * <p>部署作业经瞬时重试耗尽或业务失败后,由 {@code DeploymentConsumer} 显式投递到自定义死信队列
 * {@code deploy-job-dlq},再由 {@code DeadLetterConsumer} 落到本表,供 {@code MQMonitorController}
 * + 前端死信页查看与手动重试。死信只是审计快照,故 {@code deploymentRecordId} 用扁平字符串而非
 * {@code @ManyToOne} 关联(原作业/记录可能已被删除)。
 *
 * @author xiaozhanke
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Table(name = "dead_letter_message", uniqueConstraints = @UniqueConstraint(
        name = "uk_dead_letter_message_job_id",
        columnNames = "job_id"
))
@Comment("死信消息表")
public class DeadLetterMessage extends BasePo {
    /**
     * 死信记录 Id
     */
    @Comment("死信记录 Id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 原作业 Id(唯一索引 uk_dead_letter_message_job_id 做死信落库防重——并发重投由 DB 唯一约束收口)
     */
    @Comment("原作业 Id")
    @Column(name = "job_id", nullable = false, length = 36)
    private String jobId;

    /**
     * 关联部署记录 Id
     */
    @Comment("关联部署记录 Id")
    @Column(name = "deployment_record_id", nullable = false)
    private String deploymentRecordId;

    /**
     * 作业类型
     */
    @Comment("作业类型")
    @Column(name = "job_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private JobTypeEnum jobType;

    /**
     * 失败原因
     */
    @Comment("失败原因")
    @Column(length = 1024)
    private String errorMessage;

    /**
     * 原始消息体(JSON 文本,保留死信的原始上下文)
     */
    @Comment("原始消息体")
    @Column(columnDefinition = "text")
    private String originalPayload;

    /**
     * 进入死信的时间
     */
    @Comment("进入死信的时间")
    @Column(nullable = false)
    private LocalDateTime deadLetteredAt;

    /**
     * 是否已人工重试(重试 = 新建一份新 jobId 的作业,见 ADR-0003)
     */
    @Comment("是否已人工重试")
    @Column(nullable = false)
    private Boolean retried = Boolean.FALSE;

    /**
     * 人工重试生成的新作业 Id
     */
    @Comment("人工重试生成的新作业 Id")
    @Column(name = "retried_job_id", length = 36)
    private String retriedJobId;
}
