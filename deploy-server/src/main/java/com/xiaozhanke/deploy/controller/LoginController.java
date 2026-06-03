package com.xiaozhanke.deploy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 登录页提供：把 {@code GET /login} 转发到授权服务器自托管的静态登录页
 * {@code resources/static/login.html}。
 *
 * <p>未认证浏览器导航访问 {@code /oauth2/authorize} 时，AS 链经
 * {@code LoginUrlAuthenticationEntryPoint("/login")} 重定向到此；第三方 client 直连
 * {@code /oauth2/authorize} 同样在此看到登录页，不依赖任何前端部署（AS 自包含）。
 *
 * <p>forward 走 FORWARD dispatch、不重过滤链，default 链对 {@code /login} 的 permitAll 不受影响。
 *
 * <p><b>必须用 {@code @Controller} 而非 {@code @RestController}</b>：
 * {@link com.xiaozhanke.deploy.config.WebMvcConfig} 会给所有 {@code @RestController} 自动加 {@code /api} 前缀，
 * 且 {@code @RestController} 下 String 返回值会被当作响应体而非视图名（forward 失效）。
 *
 * @author xiaozhanke
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "forward:/login.html";
    }
}
