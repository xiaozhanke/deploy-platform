package com.xiaozhanke.deploy.exception;

/**
 * SSH 瞬时(基础设施级)故障:连接超时、网络抖动、通道中断等。
 *
 * <p>表示"基础设施级失败"——consumer 收到后做有限次短同步重试(小退避),
 * 多次后仍失败才转死信。与 {@link JobFailureException}(业务级,立即死信)区分。
 *
 * @author xiaozhanke
 */
public class SshTransientException extends RuntimeException {
    public SshTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
