package com.xiaozhanke.deploy.util;

/**
 * 错误信息入库前的长度规范化工具。
 *
 * <p>{@code deployment_job.error_message} 与 {@code dead_letter_message.error_message} 两列都按
 * {@code length = 1024} 建表(见对应实体)。作业进入死信时,失败原因可能来自 SSH 长堆栈或多段拼接文案,
 * 直接写库会触发 {@code Data too long} 异常,故统一在写入作业终态 / 投递死信消息前用
 * {@link #truncate(String)} 收口到列长上限。两处消费链路(DeploymentConsumer 投递死信消息、
 * DeploymentJobExecutionService 落作业终态)共用同一上限,避免各自维护一份重复的截断逻辑。
 *
 * @author xiaozhanke
 */
public final class ErrorMessageUtils {

    /**
     * 错误信息列长上限(与 {@code deployment_job.error_message} / {@code dead_letter_message.error_message} 一致)
     */
    public static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private ErrorMessageUtils() {
    }

    /**
     * 把错误信息截断到不超过 {@link #MAX_ERROR_MESSAGE_LENGTH} 个字符,{@code null} 原样返回。
     *
     * @param message 原始错误信息(可能为 null)
     * @return 截断后的错误信息;入参为 null 时返回 null
     */
    public static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_MESSAGE_LENGTH
                ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                : message;
    }
}
