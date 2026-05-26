package com.xiaozhanke.deploy.repository;

import com.xiaozhanke.deploy.model.entity.PlatformRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 角色持久化接口
 *
 * @author xiaozhanke
 */
@Repository
public interface PlatformRoleRepository extends JpaRepository<PlatformRole, String>,
        JpaSpecificationExecutor<PlatformRole> {
    Optional<PlatformRole> findByName(String name);

    List<PlatformRole> findByNameIn(List<String> names);
}
