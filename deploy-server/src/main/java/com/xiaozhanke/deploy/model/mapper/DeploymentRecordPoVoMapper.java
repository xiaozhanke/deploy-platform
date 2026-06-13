package com.xiaozhanke.deploy.model.mapper;

import com.xiaozhanke.deploy.model.base.BasePoVoMapper;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import com.xiaozhanke.deploy.model.vo.DeploymentRecordVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * 部署记录 PO VO 转换器
 *
 * <p>嵌套的 hostRecord、fileRecord 转换分别委托给 {@link HostPoVoMapper}、{@link FileRecordPoVoMapper},
 * 由其负责忽略 deleted、password 等无源字段。
 *
 * @author xiaozhanke
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {HostPoVoMapper.class, FileRecordPoVoMapper.class})
public interface DeploymentRecordPoVoMapper extends BasePoVoMapper<DeploymentRecord, DeploymentRecordVo> {

    /**
     * 最近作业、存活三态均由 service 查询/派生后单独回填(实体上无对应字段),映射时忽略以免 unmapped 告警。
     */
    @Override
    @Mapping(target = "latestJob", ignore = true)
    @Mapping(target = "livenessState", ignore = true)
    DeploymentRecordVo poToVo(DeploymentRecord po);

    /**
     * deleted 为软删除标记,VO 中无此字段,反向映射无源可取,忽略以免 unmapped 告警。
     */
    @Override
    @Mapping(target = "deleted", ignore = true)
    DeploymentRecord voToPo(DeploymentRecordVo vo);
}
