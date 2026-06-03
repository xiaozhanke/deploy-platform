package com.xiaozhanke.deploy.config;

import com.xiaozhanke.deploy.security.handler.LoginAuthFailureHandler;
import com.xiaozhanke.deploy.security.handler.LoginAuthSuccessHandler;
import com.xiaozhanke.deploy.security.handler.OidcLogoutErrorRedirectHandler;
import com.xiaozhanke.deploy.security.token.JwtToSecurityUserConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collections;
import java.util.function.Function;

/**
 * Spring Security 配置类
 *
 * @author xiaozhanke
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final AccessDeniedHandler accessDeniedHandler;
    private final CorsProperties corsProperties;

    public SecurityConfig(AuthenticationEntryPoint authenticationEntryPoint,
                          AccessDeniedHandler accessDeniedHandler,
                          CorsProperties corsProperties) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsProperties = corsProperties;
    }

    /**
     * OIDC 登出校验失败时的优雅降级跳转地址（固定可信路径，避免开放重定向）。
     * 默认指向前端 SPA 落地路由；relative 路径由浏览器按当前 origin 解析，dev / prod 通用。
     */
    @Value("${app.logout.error-redirect-uri:/ui/login/landing}")
    private String logoutErrorRedirectUri;

    /**
     * 配置 OAuth2 授权服务器的安全过滤链
     *
     * @param http 安全配置
     * @return 安全过滤链
     * @throws Exception 异常处理
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        // 自定义 OIDC 用户信息
        Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper = (context) -> {
            OidcUserInfoAuthenticationToken authentication = context.getAuthentication();
            JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication.getPrincipal();

            return new OidcUserInfo(principal.getToken().getClaims());
        };

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();
        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, authorizationServer ->
                        authorizationServer
                                // 启用 OpenID Connect 1.0
                                .oidc(oidc -> oidc
                                        .userInfoEndpoint(userInfo -> userInfo
                                                .userInfoMapper(userInfoMapper)
                                        )
                                        // RP-Initiated Logout 失败优雅降级：会话已失效（重启/过期）时
                                        // 不甩 Whitelabel，而是失效本端会话并跳落地页（详见该 handler 注释）
                                        .logoutEndpoint(logout -> logout
                                                .errorResponseHandler(
                                                        new OidcLogoutErrorRedirectHandler(logoutErrorRedirectUri))
                                        )
                                )
                )
                .authorizeHttpRequests(authorize -> authorize
                        // 所有请求都需要认证
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers
                        // 禁用 X-Frame-Options，改用 CSP
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                        // 配置内容安全策略 (CSP)
                        .contentSecurityPolicy(csp -> csp
                                // 允许来自 'self' 的页面嵌入
                                .policyDirectives("frame-ancestors 'self'")
                        )
                )
                // 开启 CORS 支持
                .cors(Customizer.withDefaults())
                // 关闭 CSRF 防护
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(authorizationServerConfigurer.getEndpointsMatcher())
                )
                // 使用基于 HttpSession 的有状态会话管理策略
                .securityContext(context -> context
                        .securityContextRepository(new HttpSessionSecurityContextRepository())
                )
                // 未认证导航到 AS 端点（如 /oauth2/authorize）→ 统一重定向到 /login（单一登录入口）。
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                        .accessDeniedHandler(accessDeniedHandler)
                );

        return http.build();
    }

    /**
     * 默认的安全过滤链 —— 处理登录页面、表单登录、CSRF 令牌引导（/csrf）与 OIDC discovery。
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/.well-known/**", "/login", "/login/**", "/csrf")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/.well-known/**", "/login", "/login/**", "/csrf").permitAll()
                        .anyRequest().authenticated()
                )
                // 表单登录
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login/authenticate")
                        .successHandler(authSuccessHandler())
                        .failureHandler(authFailureHandler())
                        .permitAll()
                )
                // 会话管理：登记到 SessionRegistry（OIDC 登出依赖），不限制并发
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry())
                )
                // CSRF 防护：plain CsrfTokenRequestAttributeHandler + HttpOnly cookie
                .csrf(csrf -> csrf
                        .csrfTokenRepository(new CookieCsrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )
                // 请求缓存：登录成功后自动回放被缓存的 /oauth2/authorize
                .requestCache(requestCache -> requestCache
                        .requestCache(new HttpSessionRequestCache())
                )
                .securityContext(context -> context
                        .securityContextRepository(new HttpSessionSecurityContextRepository())
                )
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("frame-ancestors 'self'")
                        )
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );
        return http.build();
    }

    /**
     * 配置 OAuth2 资源服务器的安全过滤链
     */
    @Bean
    @Order(1)
    public SecurityFilterChain resourceServerFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**", "/actuator/**", "/websocket/**")
                .authorizeHttpRequests(authorize -> authorize
                        // 仅管理员可访问 actuator
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // /websocket 升级请求放行：浏览器原生 WebSocket 握手不允许自定义 Authorization header，
                        // Bearer 只能塞进 STOMP CONNECT 帧；真正的认证在 WebSocketConfig.configureClientInboundChannel
                        // 的 ChannelInterceptor 中完成——未携带 / 无效 token 的 CONNECT 帧会被立刻拒绝，
                        // HTTP 升级返回 101 仅相当于建立了 TCP 通道，没有 STOMP CONNECT 就无法订阅或发布。
                        .requestMatchers("/websocket/**").permitAll()
                        .anyRequest().authenticated())
                // 禁用 Http Basic 认证
                .httpBasic(AbstractHttpConfigurer::disable)
                // 禁用表单登录
                .formLogin(AbstractHttpConfigurer::disable)
                // 禁用表单登出
                .logout(AbstractHttpConfigurer::disable)
                // 关闭 CSRF 防护
                .csrf(AbstractHttpConfigurer::disable)
                // 禁用匿名身份访问
                .anonymous(AbstractHttpConfigurer::disable)
                // 会话管理, 创建策略设为无状态
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // 资源服务器
                .oauth2ResourceServer(resourceServer -> resourceServer
                        // 配置 JWT
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(new JwtToSecurityUserConverter())
                        )
                        // 异常处理
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                // 异常处理
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );
        return http.build();
    }

    /**
     * SessionRegistry —— OIDC 登出的硬依赖。
     * <p>AS 链通过 {@code OAuth2ConfigurerUtils.getSessionRegistry} 自动取此 Bean，
     * 供 {@code OidcLogoutAuthenticationProvider} 按 {@code id_token_hint} 查找并失效会话。
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * 桥接容器 session 生命周期事件到 Spring，使 {@link SessionRegistry} 能感知会话失效。
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * 登录成功处理器 —— 继承 SavedRequestAware 自动回放被缓存的 /oauth2/authorize，
     * 并清零 failedLoginCount。
     */
    @Bean
    public LoginAuthSuccessHandler authSuccessHandler() {
        return new LoginAuthSuccessHandler();
    }

    /**
     * 登录失败处理器 —— 三合一：错误码回显 + failedLoginCount 自增 + LOGIN/FAILURE 审计。
     */
    @Bean
    public LoginAuthFailureHandler authFailureHandler() {
        return new LoginAuthFailureHandler();
    }

    /**
     * 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

    /**
     * 配置 CORS 规则。
     *
     * <p>允许的 Origin 列表完全由 {@code app.security.cors.allowed-origins} 驱动,
     * 不再绑死开发 profile —— 任意环境（dev/staging/pro）都通过同一配置 key 决定放行哪些前端域名。
     * pro 默认空集合，即生产侧若同域部署可保持默认；如需放行外部前端，请在对应 profile 显式列出。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许的源（空列表代表禁止跨域）
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        // 允许的方法
        configuration.setAllowedMethods(Collections.singletonList("*"));
        // 允许的头部
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        // 允许凭据
        configuration.setAllowCredentials(true);
        // 预检请求缓存时间
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有路径应用此配置
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
