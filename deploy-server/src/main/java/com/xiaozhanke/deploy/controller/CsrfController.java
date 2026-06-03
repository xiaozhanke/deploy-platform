package com.xiaozhanke.deploy.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * CSRF Token 下发端点。
 *
 * <p>静态登录页（{@code resources/static/login.html}）无法从服务端模板注入 {@code _csrf}，
 * 加载后通过 {@code fetch('/csrf', {credentials:'same-origin'})} 从本端点取 token，
 * 填入隐藏 {@code _csrf} 域后以原生表单 POST {@code /login/authenticate} 提交。
 *
 * <p>调用 {@code /csrf} 会：
 * <ol>
 *   <li>触发 {@link org.springframework.security.web.csrf.CsrfFilter} 的延迟 token 加载</li>
 *   <li>写入 {@code XSRF-TOKEN} cookie（服务端比对用）</li>
 *   <li>返回 JSON {@code {token, parameterName, headerName}}</li>
 * </ol>
 *
 * <p><b>路径必须挂在 default 链的 securityMatcher 下</b>：见
 * {@link com.xiaozhanke.deploy.config.SecurityConfig}，{@code /csrf} 已与 {@code /login}、
 * {@code /login/**} 一起列入 default 链的 {@code securityMatcher} + {@code permitAll}。
 * 该链的 {@code CsrfFilter} 执行后才会生成 token 并种 cookie；若把 {@code /csrf} 挪出该匹配范围
 * （不被任何链匹配），CsrfFilter 不执行 → token/cookie 拿不到、静默失效。
 *
 * <p><b>必须用 {@code @Controller} 而非 {@code @RestController}</b>：
 * {@link com.xiaozhanke.deploy.config.WebMvcConfig} 给所有 {@code @RestController} 自动加 {@code /api} 前缀，
 * 若用 {@code @RestController}，{@code /csrf} 会变成 {@code /api/csrf} 落到资源服务器链、未认证不可达。
 * 故用 {@code @Controller} + 方法级 {@code @ResponseBody} 返回 JSON。
 *
 * <p><b>硬约束</b>：必须搭配 plain {@link org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler}，
 * 不能替换为 {@code XorCsrfTokenRequestAttributeHandler} 或 {@code SpaCsrfTokenRequestHandler}，
 * 否则原生表单 POST 的 {@code _csrf} 参数会被当作掩码值解码、静默 403。
 *
 * @author xiaozhanke
 */
@Controller
public class CsrfController {

    @GetMapping("/csrf")
    @ResponseBody
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }
}
