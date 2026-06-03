package com.xiaozhanke.deploy.security.handler;

import com.xiaozhanke.deploy.enums.AuditOperationTypeEnum;
import com.xiaozhanke.deploy.enums.AuditOutcomeEnum;
import com.xiaozhanke.deploy.messaging.dto.AuditLogMessage;
import com.xiaozhanke.deploy.messaging.producer.AuditLogProducer;
import com.xiaozhanke.deploy.repository.PlatformUserRepository;
import com.xiaozhanke.deploy.security.exception.AuthenticationErrorCode;
import com.xiaozhanke.deploy.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import jakarta.servlet.ServletException;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 登录失败处理器 —— 三合一。
 *
 * <ol>
 *   <li><b>错误码回显</b>：经 {@link AuthenticationErrorCode} 把认证异常映射为业务错误码，
 *       重定向 {@code /login?error=<code>}（只带 code、不带后端英文 message）。</li>
 *   <li><b>失败计数自增</b>：仅对 {@link BadCredentialsException} 做原子
 *       {@code @Modifying UPDATE}，对 {@code LockedException} 不自增（冷却不被续期）。</li>
 *   <li><b>LOGIN/FAILURE 审计</b>：在请求线程内通过 {@link AuditLogProducer} 直接记录，
 *       因为 form-login 在过滤器层失败时不会进入控制器，{@code @RestControllerAdvice} 不生效。</li>
 * </ol>
 *
 * @author xiaozhanke
 */
@Slf4j
public class LoginAuthFailureHandler implements AuthenticationFailureHandler {

    private static final String FAILURE_URL_TEMPLATE = "/login?error=%s";

    @Setter(onMethod_ = @Autowired)
    private PlatformUserRepository platformUserRepository;

    @Setter(onMethod_ = @Autowired)
    private AuditLogProducer auditLogProducer;

    private final SimpleUrlAuthenticationFailureHandler delegate = new SimpleUrlAuthenticationFailureHandler();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException, ServletException {
        String errorCode = AuthenticationErrorCode.from(exception);
        String username = request.getParameter("username");

        // 仅 BadCredentialsException 触发失败计数自增；LockedException 不自增（冷却不被续期）。
        // 计数 UPDATE 的事务边界放在 repository 方法（@Transactional），不在本 handler——
        // 避免把下面同步 Kafka 审计发送包进 DB 事务、在 Kafka 抖动时长持用户行写锁。
        if (exception instanceof BadCredentialsException && username != null && !username.isBlank()) {
            platformUserRepository.incrementFailedLoginCount(username, LocalDateTime.now());
            log.info("用户 [{}] 登录失败，失败计数已递增", username);
        }

        // 在请求线程内记录 LOGIN/FAILURE 审计
        recordAudit(username, exception, errorCode, request);

        // 重定向到登录页并携带错误码
        String targetUrl = String.format(FAILURE_URL_TEMPLATE, errorCode);
        delegate.setDefaultFailureUrl(targetUrl);
        delegate.onAuthenticationFailure(request, response, exception);
    }

    /**
     * 在请求线程内记录 LOGIN/FAILURE 审计。
     */
    private void recordAudit(String username, AuthenticationException exception, String errorCode,
                             HttpServletRequest request) {
        try {
            String operator = username != null ? username : "unknown";
            String errorMessage = exception.getMessage() != null
                    ? exception.getMessage()
                    : exception.getClass().getSimpleName();
            String clientIp = ClientIpResolver.resolve(request);

            AuditLogMessage message = new AuditLogMessage(
                    operator,
                    AuditOperationTypeEnum.LOGIN,
                    operator,
                    "用户登录",
                    AuditOutcomeEnum.FAILURE,
                    errorMessage,
                    clientIp,
                    LocalDateTime.now()
            );
            auditLogProducer.send(message);
        } catch (Exception e) {
            log.warn("登录失败审计记录发送失败，已跳过: operator=[{}]", username, e);
        }
    }
}
