package com.xiaozhanke.deploy.model.mapper;

import com.xiaozhanke.deploy.model.base.BasePoDtoMapper;
import com.xiaozhanke.deploy.model.dto.HostRecordDto;
import com.xiaozhanke.deploy.model.entity.HostRecord;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 主机 PO DTO 转换器
 *
 * @author xiaozhanke
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface HostPoDtoMapper extends BasePoDtoMapper<HostRecord, HostRecordDto> {
}
