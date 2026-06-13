package com.xiaozhanke.deploy.model.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.xiaozhanke.deploy.enums.JobTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建部署作业的请求体
 *
 * <p>{@code clientRequestId} 由前端每次按钮点击生成(UUID v4),后端用
 * {@code (deploymentRecordId, jobType, clientRequestId)} 唯一索引拦截重复请求。
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

    /**
     * 期望执行时间(可选):设置后创建延迟作业,作业在达到该时间后才被消费执行。
     * 留空则立即执行(走事务消息路径)。
     */
    @Schema(description = "期望执行时间(设置后为延迟作业,留空则立即执行)", example = "2026-06-02T02:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime executeAt;

    /**
     * 目标文件记录 Id(**仅 UPDATE 作业必填**):要更新到的新应用包。其余作业类型忽略本字段。
     * 包的 SFTP 上传由前端在提交作业前单独完成,本字段只承载"更新到哪个包"的引用。
     */
    @Schema(description = "目标文件记录 Id(UPDATE 作业必填)", example = "550e8400-e29b-41d4-a716-446655440001")
    private String fileRecordId;
}
