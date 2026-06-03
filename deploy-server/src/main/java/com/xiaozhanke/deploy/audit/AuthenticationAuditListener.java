package com.xiaozhanke.deploy.audit;

import com.xiaozhanke.deploy.enums.AuditOperationTypeEnum;
import com.xiaozhanke.deploy.enums.AuditOutcomeEnum;
import com.xiaozhanke.deploy.messaging.dto.AuditLogMessage;
import com.xiaozhanke.deploy.messaging.producer.AuditLogProducer;
import com.xiaozhanke.deploy.util.ClientIpResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.session.SessionDestroyedEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证审计事件监听器。
 *
 * <p>在 form-login 替代 {@code AuthController.login()} 后，登录/登出审计从
 * {@code @Auditable} 注解迁移到此监听器：
 *
 * <ul>
 *   <li><b>LOGIN / SUCCESS</b>：监听 {@link InteractiveAuthenticationSuccessEvent}。
 *       form-login 成功后在<b>请求线程内</b>发布此事件（静默续签 {@code prompt=none}
 *       复用 AS 会话时<b>不</b>发此事件 → 续签不会刷屏审计，符合预期）。</li>
 *   <li><b>LOGIN / FAILURE</b>：不走事件，直接在
 *       {@link com.xiaozhanke.deploy.security.handler.LoginAuthFailureHandler} 中记录
 *       （与错误码回显、失败计数自增三合一，仍在请求线程）。</li>
 *   <li><b>LOGOUT</b>：监听 {@link SessionDestroyedEvent}。
 *       OIDC 登出通过 {@code SessionRegistry} 使会话失效，容器触发
 *       {@code HttpSessionDestroyedEvent} → Spring 包装为 {@code SessionDestroyedEvent}。
 *       OIDC 登出<b>不</b>发布 {@code LogoutSuccessEvent}，故以此事件兜底。</li>
 * </ul>
 *
 * <p><b>硬约束</b>：审计消息采集必须在<b>请求线程内</b>同步完成（operator、client IP
 * 都依赖请求上下文），通过 Kafka 异步落库——<b>禁止 {@code @Async}</b>。
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
public class AuthenticationAuditListener {

    private final AuditLogProducer auditLogProducer;

    public AuthenticationAuditListener(AuditLogProducer auditLogProducer) {
        this.auditLogProducer = auditLogProducer;
    }

    /**
     * LOGIN / SUCCESS：form-login 成功后记录。
     */
    @EventListener(InteractiveAuthenticationSuccessEvent.class)
    public void onLoginSuccess(InteractiveAuthenticationSuccessEvent event) {
        try {
            String operator = event.getAuthentication().getName();
            String clientIp = ClientIpResolver.resolveFromContext();
            AuditLogMessage message = new AuditLogMessage(
                    operator,
                    AuditOperationTypeEnum.LOGIN,
                    operator,
                    "用户登录",
                    AuditOutcomeEnum.SUCCESS,
                    null,
                    clientIp,
                    LocalDateTime.now()
            );
            auditLogProducer.send(message);
            log.info("登录审计已记录: operator=[{}]", operator);
        } catch (Exception e) {
            log.warn("登录成功审计记录发送失败，已跳过", e);
        }
    }

    /**
     * LOGOUT：会话销毁时记录（覆盖 OIDC 登出与会话超时）。
     *
     * <p>OIDC 登出通过 {@code SessionRegistry} 使会话失效，容器触发会话销毁事件。
     * 会话超时也会触发此事件，对审计而言均可接受。
     */
    @EventListener(SessionDestroyedEvent.class)
    public void onSessionDestroyed(SessionDestroyedEvent event) {
        try {
            List<SecurityContext> contexts = event.getSecurityContexts();
            for (SecurityContext context : contexts) {
                Authentication authentication = context.getAuthentication();
                if (authentication != null && authentication.isAuthenticated()) {
                    String operator = authentication.getName();
                    AuditLogMessage message = new AuditLogMessage(
                            operator,
                            AuditOperationTypeEnum.LOGOUT,
                            operator,
                            "用户登出",
                            AuditOutcomeEnum.SUCCESS,
                            null,
                            null, // 会话销毁事件中无法可靠获取 client IP
                            LocalDateTime.now()
                    );
                    auditLogProducer.send(message);
                    log.info("登出审计已记录: operator=[{}]", operator);
                }
            }
        } catch (Exception e) {
            log.warn("登出审计记录发送失败，已跳过", e);
        }
    }
}
