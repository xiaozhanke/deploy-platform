package com.xiaozhanke.deploy.model.mapper;

import com.xiaozhanke.deploy.model.base.BasePoVoMapper;
import com.xiaozhanke.deploy.model.entity.PlatformRole;
import com.xiaozhanke.deploy.model.vo.PlatformRoleVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * 角色 PO VO 转换器
 *
 * @author xiaozhanke
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PlatformRolePoVoMapper extends BasePoVoMapper<PlatformRole, PlatformRoleVo> {

    /**
     * deleted 为软删除标记;users 为多对多反向关联,VO 中均无此字段,
     * 反向映射无源可取,忽略以免 unmapped 告警。
     */
    @Override
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "users", ignore = true)
    PlatformRole voToPo(PlatformRoleVo vo);
}
