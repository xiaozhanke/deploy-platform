package com.xiaozhanke.deploy.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 客户端 IP 解析工具。
 *
 * <p>优先取 {@code X-Forwarded-For} 的首个地址（经 nginx / vite 代理时真实客户端 IP 在此），
 * 否则回退 {@code request.getRemoteAddr()}。审计采集多处复用，统一在此避免逻辑分叉。
 *
 * @author xiaozhanke
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /**
     * 从已持有的请求对象解析客户端 IP。
     */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 从当前线程绑定的请求上下文解析客户端 IP；无请求上下文（如非请求线程）时返回 {@code null}。
     */
    public static String resolveFromContext() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return resolve(servletRequestAttributes.getRequest());
        }
        return null;
    }
}
