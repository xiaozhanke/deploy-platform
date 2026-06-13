package com.xiaozhanke.deploy.model.mapper;

import com.xiaozhanke.deploy.model.base.BasePoVoMapper;
import com.xiaozhanke.deploy.model.entity.HostRecord;
import com.xiaozhanke.deploy.model.vo.HostRecordVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * 主机 Po Vo 转换器
 *
 * @author xiaozhanke
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface HostPoVoMapper extends BasePoVoMapper<HostRecord, HostRecordVo> {

    /**
     * online 为主机在线性,由 service 读监控内存缓存后单独回填(实体上无对应字段),映射时忽略以免 unmapped 告警。
     */
    @Override
    @Mapping(target = "online", ignore = true)
    HostRecordVo poToVo(HostRecord po);

    /**
     * deleted 为软删除标记;password、privateKeyPassword 为敏感凭据,VO 中均无此字段,
     * 反向映射无源可取,忽略以免 unmapped 告警。
     */
    @Override
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "privateKeyPassword", ignore = true)
    HostRecord voToPo(HostRecordVo vo);
}
