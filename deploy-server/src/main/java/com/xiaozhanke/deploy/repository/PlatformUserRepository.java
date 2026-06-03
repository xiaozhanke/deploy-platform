package com.xiaozhanke.deploy.repository;

import com.xiaozhanke.deploy.model.entity.PlatformUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 用户持久化接口
 *
 * <p>{@link PlatformUser#getRoles()} 已改为 {@link jakarta.persistence.FetchType#LAZY}，
 * 这里通过 {@link EntityGraph} 暴露 "带 roles" 的查询方法，避免登录 / 详情场景退化为 N+1；
 * 列表分页则在 Service 层的 Specification 内显式 {@code root.fetch("roles", LEFT)} 联合加载。
 *
 * @author xiaozhanke
 */
@Repository
public interface PlatformUserRepository extends JpaRepository<PlatformUser, String>,
        JpaSpecificationExecutor<PlatformUser> {

    Optional<PlatformUser> findByUsername(String username);

    @EntityGraph(attributePaths = "roles")
    Optional<PlatformUser> findWithRolesByUsername(String username);

    @EntityGraph(attributePaths = "roles")
    Optional<PlatformUser> findWithRolesById(String id);

    /**
     * 原子递增登录失败计数并更新最后失败时间。
     *
     * <p>按用户名更新，规避「读-改-写」竞态；用户不存在则影响 0 行，不暴露用户是否存在。
     */
    @Transactional
    @Modifying
    @Query("UPDATE PlatformUser u SET u.failedLoginCount = u.failedLoginCount + 1, "
            + "u.lastFailedLoginTime = :lastFailedTime WHERE u.username = :username")
    int incrementFailedLoginCount(@Param("username") String username,
                                  @Param("lastFailedTime") LocalDateTime lastFailedTime);

    /**
     * 原子清零登录失败计数（登录成功时调用），并清空最后失败时间。
     *
     * <p>成功登录后已无未决失败记录，故 lastFailedLoginTime 置 null，而非写入成功时间——
     * 避免「最后失败时间」列里出现一个并非失败的时间戳。
     */
    @Transactional
    @Modifying
    @Query("UPDATE PlatformUser u SET u.failedLoginCount = 0, "
            + "u.lastFailedLoginTime = null WHERE u.username = :username")
    int resetFailedLoginCount(@Param("username") String username);
}
