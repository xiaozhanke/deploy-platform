package com.xiaozhanke.deploy.model.mapper;

import com.xiaozhanke.deploy.model.base.BasePoVoMapper;
import com.xiaozhanke.deploy.model.entity.PlatformUser;
import com.xiaozhanke.deploy.model.vo.PlatformUserVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * 用户 PO VO 转换器
 *
 * <p>roles 集合元素 PlatformRoleVo 转 PlatformRole 委托给 {@link PlatformRolePoVoMapper},
 * 由其负责忽略 deleted、users 等无源字段。
 *
 * @author xiaozhanke
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = PlatformRolePoVoMapper.class)
public interface PlatformUserPoVoMapper extends BasePoVoMapper<PlatformUser, PlatformUserVo> {

    /**
     * deleted 为软删除标记;password 为敏感凭据;accountExpiredTime、passwordLastChangedTime、
     * lastFailedLoginTime、failedLoginCount 为登录安全状态,均由服务端维护,VO 中无对应字段,
     * 反向映射无源可取,忽略以免 unmapped 告警。
     */
    @Override
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "accountExpiredTime", ignore = true)
    @Mapping(target = "passwordLastChangedTime", ignore = true)
    @Mapping(target = "lastFailedLoginTime", ignore = true)
    @Mapping(target = "failedLoginCount", ignore = true)
    PlatformUser voToPo(PlatformUserVo vo);
}
