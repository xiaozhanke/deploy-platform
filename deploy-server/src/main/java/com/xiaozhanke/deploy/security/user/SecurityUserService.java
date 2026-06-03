package com.xiaozhanke.deploy.security.user;

import com.xiaozhanke.deploy.enums.UserStatusEnum;
import com.xiaozhanke.deploy.model.entity.PlatformUser;
import com.xiaozhanke.deploy.repository.PlatformUserRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证用户服务类
 *
 * @author xiaozhanke
 */
@Slf4j
@Service
public class SecurityUserService implements UserDetailsService {

    private final PlatformUserRepository platformUserRepository;
    /**
     * 密码有效期（天数）。
     *
     * <p>{@code &gt; 0}：密码自最后修改时间起 N 天内有效，过期后由 Spring Security 拒绝登录。<br>
     * {@code &lt;= 0}：禁用密码有效期约束（永不过期），仅在内网/低敏感环境使用。
     */
    @Value("${app.security.password-validity-days:365}")
    private int passwordValidityDays;
    /**
     * 允许连续登录失败的最大次数。
     *
     * <p>{@code &gt; 0}：连续失败达到该值即锁定账户，需后台解锁。<br>
     * {@code &lt;= 0}：禁用登录失败锁定（仅暴力破解防御层关闭，{@link UserStatusEnum#LOCKED} 仍然生效）。
     */
    @Value("${app.security.max-failed-logins:5}")
    private int maxFailedLogins;
    /**
     * 失败锁定冷却时长。
     *
     * <p>失败次数达阈值后进入冷却期，冷却期内 {@code LockedException}（不自增计数器，冷却不被续期）；
     * 冷却到点后自动放行一次尝试——再错则重新起算，成功则清零。
     *
     * <p>支持 {@code Duration} 格式（如 {@code 15m}、{@code 600s}、{@code PT15M}）。默认 15 分钟。
     */
    @Value("${app.security.lockout-cooldown:15m}")
    private Duration lockoutCooldown;

    public SecurityUserService(PlatformUserRepository platformUserRepository) {
        this.platformUserRepository = platformUserRepository;
    }

    /**
     * 启动后输出当前生效的安全策略，避免 0 / 负值这种"静默放行"配置无人察觉。
     */
    @PostConstruct
    void logSecurityPolicy() {
        if (passwordValidityDays <= 0) {
            log.warn("密码有效期已禁用（app.security.password-validity-days={}），密码将永不过期", passwordValidityDays);
        } else {
            log.info("密码有效期 {} 天", passwordValidityDays);
        }
        if (maxFailedLogins <= 0) {
            log.warn("登录失败锁定已禁用（app.security.max-failed-logins={}），账户不会因连续失败被自动锁定",
                    maxFailedLogins);
        } else {
            log.info("登录失败锁定阈值 {} 次", maxFailedLogins);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 必须用 findWithRolesByUsername：roles 改 LAZY 后，open-in-view=false 下脱离事务访问会抛 LazyInitializationException
        PlatformUser user = platformUserRepository.findWithRolesByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(String.format("用户 [%s] 未找到", username)));

        Set<GrantedAuthority> authorities =
                user.getRoles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName())).collect(Collectors.toSet());

        // 用户启用状态
        boolean enabled = user.getStatus() == UserStatusEnum.ACTIVE || user.getStatus() == UserStatusEnum.INITIALIZED;
        // 用户未过期状态
        boolean accountNonExpired = true;
        if (user.getAccountExpiredTime() != null) {
            accountNonExpired = user.getAccountExpiredTime().isAfter(LocalDateTime.now());
        }
        // 用户未锁定状态：LOCKED = 手工永久锁定；失败锁定走时间窗冷却
        boolean accountNonLocked = isAccountNonLocked(user.getStatus(), user.getFailedLoginCount(),
                user.getLastFailedLoginTime());
        // 密码未过期状态：passwordValidityDays <= 0 视为永不过期
        boolean credentialsNonExpired = isCredentialsNonExpired(user.getPasswordLastChangedTime());

        return new SecurityUser(user.getUsername(), user.getDisplayName(), user.getPassword(), user.getPhone(),
                user.getEmail(), authorities, enabled, accountNonExpired, accountNonLocked, credentialsNonExpired);
    }

    /**
     * 判断账户是否未被失败锁定（含时间窗冷却）。
     *
     * <p>判定优先级：
     * <ol>
     *   <li>{@link UserStatusEnum#LOCKED} → 永久锁定（管理员手工操作）</li>
     *   <li>{@code maxFailedLogins <= 0} → 未启用失败锁定，始终放行</li>
     *   <li>{@code failedLoginCount < maxFailedLogins} → 未达阈值，放行</li>
     *   <li>已达阈值但无失败时间记录 → 锁定</li>
     *   <li>已达阈值且在冷却窗口内 → 锁定</li>
     *   <li>已达阈值但冷却已到 → 自动放行一次（固定冷却，非滑动）</li>
     * </ol>
     *
     * @param status             账户状态
     * @param failedLoginCount   当前连续失败次数
     * @param lastFailedLoginTime 最后一次登录失败时间，可能为 null
     * @return true 表示账户未被锁定
     */
    boolean isAccountNonLocked(UserStatusEnum status, int failedLoginCount,
                               LocalDateTime lastFailedLoginTime) {
        // 1. 手工永久锁定
        if (status == UserStatusEnum.LOCKED) {
            return false;
        }
        // 2. 未启用失败锁定
        if (maxFailedLogins <= 0) {
            return true;
        }
        // 3. 未达阈值
        if (failedLoginCount < maxFailedLogins) {
            return true;
        }
        // 4. 已无失败时间记录 → 锁定（防御性）
        if (lastFailedLoginTime == null) {
            return false;
        }
        // 5-6. 时间窗冷却：冷却到点则自动放行，冷却内保持锁定
        return lastFailedLoginTime.plus(lockoutCooldown).isBefore(LocalDateTime.now());
    }

    /**
     * 判断密码是否在有效期内。
     *
     * @param passwordLastChangedTime 密码上次修改时间，可能为 null（如初始化用户）
     * @return true 表示凭证未过期
     */
    boolean isCredentialsNonExpired(LocalDateTime passwordLastChangedTime) {
        if (passwordValidityDays <= 0 || passwordLastChangedTime == null) {
            return true;
        }
        return passwordLastChangedTime.plusDays(passwordValidityDays).isAfter(LocalDateTime.now());
    }

    /**
     * 测试入口：注入有效期天数。
     */
    void setPasswordValidityDays(int passwordValidityDays) {
        this.passwordValidityDays = passwordValidityDays;
    }

    /**
     * 测试入口：注入失败锁定阈值。
     */
    void setMaxFailedLogins(int maxFailedLogins) {
        this.maxFailedLogins = maxFailedLogins;
    }

    /**
     * 测试入口：注入锁定冷却时长。
     */
    void setLockoutCooldown(Duration lockoutCooldown) {
        this.lockoutCooldown = lockoutCooldown;
    }
}
