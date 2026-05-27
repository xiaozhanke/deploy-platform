package com.xiaozhanke.deploy.model.request;

import com.xiaozhanke.deploy.enums.JobTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建部署作业的请求体
 *
 * <p>{@code clientRequestId} 由前端每次按钮点击生成(UUID v4),后端用
 * {@code (deploymentRecordId, jobType, clientRequestId)} 唯一索引拦截重复请求
 * (CONTEXT.md「客户端请求 Id」与 ADR-0002 的第二道防重)。
 *
 * @author xiaozhanke
 */
@Data
@Schema(description = "创建部署作业请求")
public class CreateJobRequest {

    /**
     * 作业类型
     */
    @Schema(description = "作业类型", example = "START")
    @NotNull(message = "作业类型不能为空")
    private JobTypeEnum jobType;

    /**
     * 客户端请求 Id(前端按钮点击时生成的 UUID v4,长度 36)
     */
    @Schema(description = "客户端请求 Id (UUID v4)", example = "550e8400-e29b-41d4-a716-446655440000")
    @NotBlank(message = "客户端请求 Id 不能为空")
    @Size(min = 36, max = 36, message = "客户端请求 Id 必须是 36 位 UUID")
    private String clientRequestId;
}
