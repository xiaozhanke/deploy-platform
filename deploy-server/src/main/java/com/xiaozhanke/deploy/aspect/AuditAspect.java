package com.xiaozhanke.deploy.aspect;

import com.xiaozhanke.deploy.enums.AuditOutcomeEnum;
import com.xiaozhanke.deploy.messaging.dto.AuditLogMessage;
import com.xiaozhanke.deploy.messaging.producer.AuditLogProducer;
import com.xiaozhanke.deploy.util.AuthenticationHelper;
import com.xiaozhanke.deploy.util.ClientIpResolver;
import com.xiaozhanke.deploy.util.ErrorMessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 操作审计切面(对应 MQ 方案稿场景 4、ADR-0005)。
 *
 * <p><b>双切面</b>:{@code @AfterReturning} 记 {@link AuditOutcomeEnum#SUCCESS},{@code @AfterThrowing}
 * 记 {@link AuditOutcomeEnum#FAILURE}——失败/未授权的尝试也是安全事件,单 {@code @AfterReturning} 会漏。
 *
 * <p>操作人、客户端 IP 在<b>请求线程</b>采集(此时 SecurityContext / RequestAttributes 仍在),随
 * {@link AuditLogMessage} 发往 Kafka;消费端在另一线程落库,故这些上下文必须在此刻取齐。审计切面对业务
 * <b>零侵入</b>:任何异常(SpEL 求值、发送)只记日志,绝不向业务方法抛出。
 *
 * @author xiaozhanke
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    /**
     * target 字段安全上限(与 {@code audit_log.target} 列长一致),超长命令/路径截断。
     */
    private static final int MAX_TARGET_LENGTH = 512;

    private final AuditLogProducer auditLogProducer;
    private final AuthenticationHelper authenticationHelper;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @AfterReturning("@annotation(auditable)")
    public void onSuccess(JoinPoint joinPoint, Auditable auditable) {
        record(joinPoint, auditable, AuditOutcomeEnum.SUCCESS, null);
    }

    @AfterThrowing(pointcut = "@annotation(auditable)", throwing = "ex")
    public void onFailure(JoinPoint joinPoint, Auditable auditable, Throwable ex) {
        record(joinPoint, auditable, AuditOutcomeEnum.FAILURE, ex);
    }

    private void record(JoinPoint joinPoint, Auditable auditable, AuditOutcomeEnum outcome, Throwable ex) {
        try {
            EvaluationContext evaluationContext = buildEvaluationContext(joinPoint);
            String operator = resolveOperator(auditable, evaluationContext);
            String target = truncateTarget(evaluate(auditable.target(), evaluationContext));
            String description = evaluate(auditable.description(), evaluationContext);
            if (description == null || description.isBlank()) {
                description = auditable.operationType().getDescription();
            }
            String errorMessage = ex == null ? null
                    : ErrorMessageUtils.truncate(ex.getMessage() != null ? ex.getMessage() : ex.toString());

            AuditLogMessage message = new AuditLogMessage(operator, auditable.operationType(), target,
                    description, outcome, errorMessage, ClientIpResolver.resolveFromContext(), LocalDateTime.now());
            auditLogProducer.send(message);
        } catch (Exception e) {
            // 审计绝不影响业务:吞掉所有异常,仅记日志
            log.warn("审计切面处理失败,跳过该条审计: type=[{}]", auditable.operationType(), e);
        }
    }

    private EvaluationContext buildEvaluationContext(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return new MethodBasedEvaluationContext(
                joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNameDiscoverer);
    }

    /**
     * 操作人解析:注解 SpEL 优先(如登出取被登出者)→ SecurityContext 当前用户 → anonymous 兜底。
     */
    private String resolveOperator(Auditable auditable, EvaluationContext evaluationContext) {
        String fromExpression = evaluate(auditable.operator(), evaluationContext);
        if (fromExpression != null && !fromExpression.isBlank()) {
            return fromExpression;
        }
        return authenticationHelper.getCurrentUserName().orElse("anonymous");
    }

    private String evaluate(String expression, EvaluationContext evaluationContext) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        Object value = expressionParser.parseExpression(expression).getValue(evaluationContext);
        return value == null ? null : value.toString();
    }

    private String truncateTarget(String target) {
        if (target == null) {
            return null;
        }
        return target.length() <= MAX_TARGET_LENGTH ? target : target.substring(0, MAX_TARGET_LENGTH);
    }
}
