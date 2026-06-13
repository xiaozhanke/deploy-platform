package com.xiaozhanke.deploy.model.mapper;

import com.xiaozhanke.deploy.model.base.BasePoVoMapper;
import com.xiaozhanke.deploy.model.entity.FileRecord;
import com.xiaozhanke.deploy.model.vo.FileRecordVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * 文件记录 PO VO 转换器
 *
 * @author xiaozhanke
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FileRecordPoVoMapper extends BasePoVoMapper<FileRecord, FileRecordVo> {

    /**
     * deleted 为软删除标记,VO 中无此字段,反向映射无源可取,忽略以免 unmapped 告警。
     */
    @Override
    @Mapping(target = "deleted", ignore = true)
    FileRecord voToPo(FileRecordVo vo);
}
