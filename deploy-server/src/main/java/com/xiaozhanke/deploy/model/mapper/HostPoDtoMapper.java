package com.xiaozhanke.deploy.model.mapper;

import com.xiaozhanke.deploy.model.base.BasePoDtoMapper;
import com.xiaozhanke.deploy.model.dto.HostRecordDto;
import com.xiaozhanke.deploy.model.entity.HostRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * 主机 PO DTO 转换器
 *
 * @author xiaozhanke
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface HostPoDtoMapper extends BasePoDtoMapper<HostRecord, HostRecordDto> {

    /**
     * deleted 为软删除标记,DTO 中无此字段,反向映射无源可取,忽略以免 unmapped 告警。
     */
    @Override
    @Mapping(target = "deleted", ignore = true)
    HostRecord dtoToPo(HostRecordDto dto);
}
