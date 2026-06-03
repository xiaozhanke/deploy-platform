package com.xiaozhanke.deploy.security.exception;

import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;

/**
 * 认证失败业务错误码映射。
 *
 * <p>把 Spring Security 的认证异常子类统一映射为前端可识别的错误码，HTTP status 始终是 401。
 * 过滤器层（{@code LoginAuthFailureHandler}）与控制器兜底（{@code GlobalExceptionHandler}）
 * 共用此映射，避免两处各写一份 switch 漂移。前端按 code 文案化，不依赖后端英文 message。
 *
 * @author xiaozhanke
 */
public final class AuthenticationErrorCode {

    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String CREDENTIALS_EXPIRED = "CREDENTIALS_EXPIRED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String ACCOUNT_EXPIRED = "ACCOUNT_EXPIRED";
    public static final String AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";

    private AuthenticationErrorCode() {
    }

    /**
     * 按异常子类映射错误码，未知子类兜底 {@link #AUTHENTICATION_FAILED}。
     */
    public static String from(AuthenticationException exception) {
        return switch (exception) {
            case BadCredentialsException ignored -> INVALID_CREDENTIALS;
            case CredentialsExpiredException ignored -> CREDENTIALS_EXPIRED;
            case LockedException ignored -> ACCOUNT_LOCKED;
            case DisabledException ignored -> ACCOUNT_DISABLED;
            case AccountExpiredException ignored -> ACCOUNT_EXPIRED;
            default -> AUTHENTICATION_FAILED;
        };
    }
}
