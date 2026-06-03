package com.xiaozhanke.deploy.security.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

/**
 * OIDC RP-Initiated Logout 失败的优雅降级处理器。
 *
 * <p>背景：Spring Authorization Server 的 {@code OidcLogoutAuthenticationProvider} 凭 {@code id_token_hint}
 * 里的 {@code sid} 到 {@link org.springframework.security.core.session.SessionRegistry} 查会话；查不到即抛
 * {@code invalid_token}（参数 {@code sid}），默认渲染 Whitelabel 错误页。
 *
 * <p>而 {@code SessionRegistryImpl} 是<b>纯内存</b>的——服务端重启（开发期 devtools 热重启、生产发版）或会话自然
 * 过期后，浏览器侧仍持有旧 {@code id_token}（带旧 {@code sid}），此时登出必然查不到会话。但「会话已不在」本就等价于
 * 「已登出」，不该把用户甩到错误页。
 *
 * <p>本处理器：记录 warn 日志 → <b>防御性失效当前会话</b>（即便走到这，也确保本端会话真的被清掉，不留下能被静默续签
 * 复活的活会话）→ 清 {@code SecurityContext} → 302 跳转到落地页，由前端 {@code removeUser} + 重新发起授权完成闭环。
 *
 * <p>重定向目标是构造传入的<b>固定可信路径</b>（非请求携带的 {@code post_logout_redirect_uri}），不引入开放重定向。
 *
 * @author xiaozhanke
 */
@Slf4j
public class OidcLogoutErrorRedirectHandler implements AuthenticationFailureHandler {

    private final String redirectUri;

    public OidcLogoutErrorRedirectHandler(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("OIDC 登出校验失败（多为会话已失效 / 服务端重启丢失 SessionRegistry），按已登出处理并跳转落地页。原因: {}",
                exception.getMessage());

        // 防御性失效当前会话：即便 sid 查不到，也确保本端会话被清掉，杜绝静默续签复活
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        response.sendRedirect(redirectUri);
    }
}
