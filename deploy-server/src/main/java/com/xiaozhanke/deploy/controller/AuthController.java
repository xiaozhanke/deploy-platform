package com.xiaozhanke.deploy.controller;

import com.xiaozhanke.deploy.model.vo.PlatformUserVo;
import com.xiaozhanke.deploy.service.PlatformUserService;
import com.xiaozhanke.deploy.util.AuthenticationHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * @author xiaozhanke
 */
@Tag(name = "auth", description = "认证接口")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final PlatformUserService platformUserService;
    private final AuthenticationHelper authenticationHelper;

    public AuthController(PlatformUserService platformUserService, AuthenticationHelper authenticationHelper) {
        this.platformUserService = platformUserService;
        this.authenticationHelper = authenticationHelper;
    }

    /**
     * 获取当前登录用户信息。
     *
     * @return 当前登录用户信息
     */
    @Operation(summary = "获取当前用户", description = "获取当前登录用户信息")
    @GetMapping("/me")
    public PlatformUserVo currentUser() {
        String currentUserName = authenticationHelper.getCurrentUserName()
                .orElseThrow(() -> new IllegalStateException("用户未认证"));
        return platformUserService.queryUserByUsername(currentUserName);
    }

}
