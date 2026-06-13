package com.xiaozhanke.deploy.model.vo;

import com.xiaozhanke.deploy.enums.AuditOperationTypeEnum;
import com.xiaozhanke.deploy.enums.AuditOutcomeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志 VO 类。
 *
 * <p>有意不继承 {@code BaseVo}:审计日志无 updateUser/updateTime 等审计字段,操作人与时间
 * 是一等业务字段(见 {@code AuditLog} 实体的说明)。
 *
 * @author xiaozhanke
 */
@Data
@Schema(description = "操作审计日志")
public class AuditLogVo {
    /**
     * 审计记录 Id
     */
    @Schema(description = "审计记录 Id")
    private String id;

    /**
     * 操作人
     */
    @Schema(description = "操作人")
    private String operator;

    /**
     * 操作类型
     */
    @Schema(description = "操作类型")
    private AuditOperationTypeEnum operationType;

    /**
     * 操作目标
     */
    @Schema(description = "操作目标")
    private String target;

    /**
     * 操作描述
     */
    @Schema(description = "操作描述")
    private String description;

    /**
     * 操作结果
     */
    @Schema(description = "操作结果")
    private AuditOutcomeEnum outcome;

    /**
     * 失败原因
     */
    @Schema(description = "失败原因")
    private String errorMessage;

    /**
     * 客户端 IP
     */
    @Schema(description = "客户端 IP")
    private String clientIp;

    /**
     * 操作发生时间
     */
    @Schema(description = "操作发生时间")
    private LocalDateTime operationTime;
}
