package com.xiaozhanke.deploy.model.mapper;

import com.xiaozhanke.deploy.model.base.BasePoVoMapper;
import com.xiaozhanke.deploy.model.entity.AuditLog;
import com.xiaozhanke.deploy.model.vo.AuditLogVo;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 操作审计日志 PO VO 转换器(字段同名,MapStruct 自动映射)。
 *
 * @author xiaozhanke
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuditLogPoVoMapper extends BasePoVoMapper<AuditLog, AuditLogVo> {
}
