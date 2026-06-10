package com.xiaozhanke.deploy.model.mapper;

import com.xiaozhanke.deploy.model.base.BasePoVoMapper;
import com.xiaozhanke.deploy.model.entity.HostRecord;
import com.xiaozhanke.deploy.model.vo.HostRecordVo;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 主机 Po Vo 转换器
 *
 * @author xiaozhanke
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface HostPoVoMapper extends BasePoVoMapper<HostRecord, HostRecordVo> {
}
