package com.xiaozhanke.deploy.model.mapper;

import com.xiaozhanke.deploy.model.base.BasePoVoMapper;
import com.xiaozhanke.deploy.model.entity.DeadLetterMessage;
import com.xiaozhanke.deploy.model.vo.DeadLetterMessageVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * 死信消息 PO VO 转换器(字段同名,MapStruct 自动映射)。
 *
 * @author xiaozhanke
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DeadLetterMessagePoVoMapper extends BasePoVoMapper<DeadLetterMessage, DeadLetterMessageVo> {

    /**
     * deleted 为软删除标记,VO 中无此字段,反向映射无源可取,忽略以免 unmapped 告警。
     */
    @Override
    @Mapping(target = "deleted", ignore = true)
    DeadLetterMessage voToPo(DeadLetterMessageVo vo);
}
