package com.xiaozhanke.deploy.aspect;

import com.xiaozhanke.deploy.enums.AuditOperationTypeEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解。
 *
 * <p>标注在 Controller / Service 方法上,由 {@code AuditAspect} 的 {@code @AfterReturning} +
 * {@code @AfterThrowing} 双切面拦截,异步采集到 Kafka。{@link #target} / {@link #operator} 支持 SpEL,
 * 在方法入参上取值(如登录用户名、文件 Id、被登出者),编译需带 {@code -parameters}(Spring Boot 默认开启)。
 *
 * @author xiaozhanke
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * 操作类型(必填)。
     */
    AuditOperationTypeEnum operationType();

    /**
     * 操作描述(静态文案);留空则取 {@link AuditOperationTypeEnum#getDescription()}。
     */
    String description() default "";

    /**
     * 操作目标的 SpEL 表达式(对方法入参求值),如 {@code "#id"}、{@code "#loginRequest.username"}。留空则不记目标。
     */
    String target() default "";

    /**
     * 操作人的 SpEL 表达式(可选);留空时从 SecurityContext 取当前用户,再兜底 {@code anonymous}。
     * 登出场景方法返回后上下文已清空,需用 {@code "#authentication?.name"} 从入参取被登出者。
     */
    String operator() default "";
}
