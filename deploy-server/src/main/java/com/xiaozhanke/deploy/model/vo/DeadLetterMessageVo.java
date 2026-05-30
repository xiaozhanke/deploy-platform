package com.xiaozhanke.deploy.model.vo;

import com.xiaozhanke.deploy.enums.JobTypeEnum;
import com.xiaozhanke.deploy.model.base.BaseVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 死信消息 VO 类
 *
 * @author xiaozhanke
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "死信消息信息")
public class DeadLetterMessageVo extends BaseVo {
    /**
     * 死信记录 Id
     */
    @Schema(description = "死信记录 Id")
    private String id;

    /**
     * 原作业 Id
     */
    @Schema(description = "原作业 Id")
    private String jobId;

    /**
     * 关联部署记录 Id
     */
    @Schema(description = "关联部署记录 Id")
    private String deploymentRecordId;

    /**
     * 作业类型
     */
    @Schema(description = "作业类型")
    private JobTypeEnum jobType;

    /**
     * 失败原因
     */
    @Schema(description = "失败原因")
    private String errorMessage;

    /**
     * 原始消息体
     */
    @Schema(description = "原始消息体")
    private String originalPayload;

    /**
     * 进入死信的时间
     */
    @Schema(description = "进入死信的时间")
    private LocalDateTime deadLetteredAt;

    /**
     * 是否已人工重试
     */
    @Schema(description = "是否已人工重试")
    private Boolean retried;

    /**
     * 人工重试生成的新作业 Id
     */
    @Schema(description = "人工重试生成的新作业 Id")
    private String retriedJobId;
}
