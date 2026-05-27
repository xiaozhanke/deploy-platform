package com.xiaozhanke.deploy.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 部署作业类型枚举
 *
 * <p>对应 CONTEXT.md「作业类型」词条。RESTART 在执行层等价于 STOP→START,但作为独立类型存在以承载用户意图。
 *
 * @author xiaozhanke
 */
@Getter
@AllArgsConstructor
public enum JobTypeEnum {
    /**
     * 启动应用
     */
    START("启动"),

    /**
     * 停止应用
     */
    STOP("停止"),

    /**
     * 重启应用
     */
    RESTART("重启"),

    /**
     * 更新版本(详见 CONTEXT.md「作业类型」对 UPDATE 的说明)
     */
    UPDATE("更新");

    private final String description;
}
