package com.xiaozhanke.deploy.model.entity;

import com.xiaozhanke.deploy.enums.JobStatusEnum;
import com.xiaozhanke.deploy.enums.JobTypeEnum;
import com.xiaozhanke.deploy.model.base.BasePo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * 部署作业 PO 类
 *
 * <p>对一份 {@link DeploymentRecord} 执行一次具体动作的可重试单元。详见 CONTEXT.md「部署作业」词条以及
 * ADR-0001(顺序键)、ADR-0002(幂等)、ADR-0003(重试与死信)、ADR-0004(取消语义)。
 *
 * @author xiaozhanke
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Table(name = "deployment_job", uniqueConstraints = @UniqueConstraint(
        name = "uk_deployment_job_record_type_request",
        columnNames = {"deployment_record_id", "job_type", "client_request_id"}
))
@Comment("部署作业表")
public class DeploymentJob extends BasePo {
    /**
     * 作业 Id
     */
    @Comment("作业 Id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 关联部署记录(MQ 消息的主语始终是作业,部署记录预先存在)
     */
    @Comment("关联部署记录")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_record_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_deployment_job_deployment_record"))
    private DeploymentRecord deploymentRecord;

    /**
     * 作业类型
     */
    @Comment("作业类型")
    @Column(name = "job_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private JobTypeEnum jobType;

    /**
     * 作业状态
     */
    @Comment("作业状态")
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private JobStatusEnum status;

    /**
     * 客户端请求 Id(UUID v4),与 (deployment_record_id, job_type) 组成唯一索引以做 HTTP 入口防重
     */
    @Comment("客户端请求 Id")
    @Column(name = "client_request_id", nullable = false, length = 36)
    private String clientRequestId;

    /**
     * 应用层重试次数(不包含 MQ 内部重投)
     */
    @Comment("应用层重试次数")
    @Column(nullable = false)
    private Integer retryCount = 0;

    /**
     * 错误信息(失败时记录)
     */
    @Comment("错误信息")
    @Column(length = 1024)
    private String errorMessage;

    /**
     * 开始执行时间(CAS 占据成功时刻)
     */
    @Comment("开始执行时间")
    @Column
    private LocalDateTime startTime;

    /**
     * 结束时间(无论成功失败都记录)
     */
    @Comment("结束时间")
    @Column
    private LocalDateTime endTime;
}
