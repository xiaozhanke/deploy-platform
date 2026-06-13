package com.xiaozhanke.deploy.model.mapper;

import com.xiaozhanke.deploy.model.base.BasePoDtoMapper;
import com.xiaozhanke.deploy.model.dto.FileRecordDto;
import com.xiaozhanke.deploy.model.entity.FileRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * 文件记录 PO DTO 转换器
 *
 * @author xiaozhanke
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FileRecordPoDtoMapper extends BasePoDtoMapper<FileRecord, FileRecordDto> {

    /**
     * deleted 为软删除标记,DTO 中无此字段,反向映射无源可取,忽略以免 unmapped 告警。
     */
    @Override
    @Mapping(target = "deleted", ignore = true)
    FileRecord dtoToPo(FileRecordDto dto);
}
