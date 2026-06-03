package com.xiaozhanke.deploy.security.handler;

import com.xiaozhanke.deploy.repository.PlatformUserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.io.IOException;

/**
 * 登录成功处理器。
 *
 * <p>在 Spring 自带的 {@link SavedRequestAwareAuthenticationSuccessHandler}（取出被缓存的
 * {@code /oauth2/authorize?...} 回放，无缓存则回默认目标）之上，仅追加一步：清零 failedLoginCount
 * 并清空最后失败时间（原子 UPDATE，避免读-改-写竞态）。
 *
 * <p>跳转目标无需手动按 {@code X-Forwarded-*} 重写：{@code server.forward-headers-strategy=framework}
 * 下请求在被缓存前已由 {@code ForwardedHeaderFilter} 重写为代理地址，SavedRequest 即代理地址。
 *
 * @author xiaozhanke
 */
@Slf4j
public class LoginAuthSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    @Setter(onMethod_ = @Autowired)
    private PlatformUserRepository platformUserRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {
        // 登录成功 → 清零 failedLoginCount 并清空最后失败时间（事务边界在 repository 方法 @Transactional 上）
        platformUserRepository.resetFailedLoginCount(authentication.getName());
        log.info("登录成功，回放缓存请求或跳默认目标: operator=[{}]", authentication.getName());
        // 缓存请求回放 / 默认目标跳转交由父类
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
