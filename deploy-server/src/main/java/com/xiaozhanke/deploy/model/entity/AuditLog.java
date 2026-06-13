package com.xiaozhanke.deploy.model.entity;

import com.xiaozhanke.deploy.enums.AuditOperationTypeEnum;
import com.xiaozhanke.deploy.enums.AuditOutcomeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * 操作审计日志 PO 类。
 *
 * <p><b>有意不继承 {@code BasePo}</b>:审计日志是只读旁路数据,语义上不需要 {@code updateUser /
 * updateTime / is_deleted}。更关键的是,本表由 {@code AuditLogConsumer} 在 <b>Kafka 消费线程</b>
 * 上落库,该线程没有 {@code SecurityContext}——若沿用 {@code BasePo} 的 {@code @CreatedBy},
 * {@code createUser} 会被 {@code AuditorAware} 兜底成 {@code "system"},把真正的操作人吞掉。
 * 因此操作人在切面(请求线程)捕获为业务字段 {@link #operator},随 Kafka 消息体传到消费端显式落库。
 *
 * <p>{@code operator / target} 用扁平字符串而非 {@code @ManyToOne} 关联——审计是历史快照,
 * 关联的用户/服务器/文件后续可能被删除,扁平存储才能留痕。
 *
 * @author xiaozhanke
 */
@Data
@Accessors(chain = true)
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_log_operator", columnList = "operator"),
        @Index(name = "idx_audit_log_operation_time", columnList = "operation_time")
})
@Comment("操作审计日志表")
public class AuditLog {
    /**
     * 审计记录 Id
     */
    @Comment("审计记录 Id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 操作人(切面在请求线程从 SecurityContext 捕获;匿名/失败登录为 anonymous)
     */
    @Comment("操作人")
    @Column(nullable = false)
    private String operator;

    /**
     * 操作类型
     */
    @Comment("操作类型")
    @Column(name = "operation_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditOperationTypeEnum operationType;

    /**
     * 操作目标(SpEL 从方法入参提取,如服务器 Id / 文件 Id / 命令 / 登录用户名;超长截断到 512)
     */
    @Comment("操作目标")
    @Column(length = 512)
    private String target;

    /**
     * 操作描述(注解上的静态文案)
     */
    @Comment("操作描述")
    @Column
    private String description;

    /**
     * 操作结果
     */
    @Comment("操作结果")
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditOutcomeEnum outcome;

    /**
     * 失败原因(outcome=FAILURE 时记录)
     */
    @Comment("失败原因")
    @Column(length = 1024)
    private String errorMessage;

    /**
     * 客户端 IP
     */
    @Comment("客户端 IP")
    @Column(name = "client_ip", length = 64)
    private String clientIp;

    /**
     * 操作发生时间(切面在请求线程记录,非落库时间)
     */
    @Comment("操作发生时间")
    @Column(name = "operation_time", nullable = false)
    private LocalDateTime operationTime;
}
