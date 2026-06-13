package com.xiaozhanke.deploy.exception;

/**
 * 部署作业业务级失败:命令退出码非 0、端口被占用、jar 路径错误、不支持的作业类型等。
 *
 * <p>表示"业务级失败"——重试再多也不会成功,consumer 收到后**不重试**,
 * 直接置作业 DEAD 并投递死信队列。与 {@link SshTransientException}(瞬时,可短重试)区分。
 *
 * @author xiaozhanke
 */
public class JobFailureException extends RuntimeException {
    public JobFailureException(String message) {
        super(message);
    }

    public JobFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
