package com.xiaozhanke.deploy.model.mapper;

import com.xiaozhanke.deploy.model.base.BasePoDtoMapper;
import com.xiaozhanke.deploy.model.dto.DeploymentRecordDto;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * 部署记录 PO DTO 转换器
 *
 * <p>嵌套的 hostRecord、fileRecord 转换分别委托给 {@link HostPoDtoMapper}、{@link FileRecordPoDtoMapper},
 * 由其负责忽略 deleted 等无源字段。
 *
 * @author xiaozhanke
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {HostPoDtoMapper.class, FileRecordPoDtoMapper.class})
public interface DeploymentRecordPoDtoMapper extends BasePoDtoMapper<DeploymentRecord, DeploymentRecordDto> {

    /**
     * deleted 为软删除标记,DTO 中无此字段,反向映射无源可取,忽略以免 unmapped 告警。
     */
    @Override
    @Mapping(target = "deleted", ignore = true)
    DeploymentRecord dtoToPo(DeploymentRecordDto dto);
}
