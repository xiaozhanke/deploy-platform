package com.xiaozhanke.deploy.model.mapper;

import com.xiaozhanke.deploy.model.base.BasePoVoMapper;
import com.xiaozhanke.deploy.model.entity.DeadLetterMessage;
import com.xiaozhanke.deploy.model.vo.DeadLetterMessageVo;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 死信消息 PO VO 转换器(字段同名,MapStruct 自动映射)。
 *
 * @author xiaozhanke
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DeadLetterMessagePoVoMapper extends BasePoVoMapper<DeadLetterMessage, DeadLetterMessageVo> {
}
