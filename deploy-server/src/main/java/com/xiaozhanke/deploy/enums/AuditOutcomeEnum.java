package com.xiaozhanke.deploy.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审计结果枚举。
 *
 * <p>{@code @AfterReturning} 切面记 {@link #SUCCESS},{@code @AfterThrowing} 切面记 {@link #FAILURE}
 * ——失败/未授权的尝试也要进审计流,单 {@code @AfterReturning} 会漏掉安全事件。
 *
 * @author xiaozhanke
 */
@Getter
@AllArgsConstructor
public enum AuditOutcomeEnum {
    /**
     * 操作成功
     */
    SUCCESS("成功"),

    /**
     * 操作失败(含未授权、异常)
     */
    FAILURE("失败");

    private final String description;
}
