package com.xiaozhanke.deploy.model.vo;

import com.xiaozhanke.deploy.enums.JobStatusEnum;
import com.xiaozhanke.deploy.enums.JobTypeEnum;
import com.xiaozhanke.deploy.model.base.BaseVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 部署作业 VO 类
 *
 * @author xiaozhanke
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "部署作业信息")
public class DeploymentJobVo extends BaseVo {
    /**
     * 作业 Id
     */
    @Schema(description = "作业 Id")
    private String id;

    /**
     * 关联部署记录 Id(扁平化暴露,不嵌套整份 DeploymentRecordVo)
     */
    @Schema(description = "关联部署记录 Id")
    private String deploymentRecordId;

    /**
     * 作业类型
     */
    @Schema(description = "作业类型")
    private JobTypeEnum jobType;

    /**
     * 作业状态
     */
    @Schema(description = "作业状态")
    private JobStatusEnum status;

    /**
     * 客户端请求 Id
     */
    @Schema(description = "客户端请求 Id")
    private String clientRequestId;

    /**
     * 应用层重试次数
     */
    @Schema(description = "应用层重试次数")
    private Integer retryCount;

    /**
     * 错误信息
     */
    @Schema(description = "错误信息")
    private String errorMessage;

    /**
     * 开始执行时间
     */
    @Schema(description = "开始执行时间")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
