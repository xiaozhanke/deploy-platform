package com.xiaozhanke.deploy.model.mapper;

import com.xiaozhanke.deploy.model.base.BasePoVoMapper;
import com.xiaozhanke.deploy.model.entity.DeploymentJob;
import com.xiaozhanke.deploy.model.vo.DeploymentJobVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * 部署作业 PO VO 转换器
 *
 * @author xiaozhanke
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DeploymentJobPoVoMapper extends BasePoVoMapper<DeploymentJob, DeploymentJobVo> {

    @Override
    @Mapping(source = "deploymentRecord.id", target = "deploymentRecordId")
    DeploymentJobVo poToVo(DeploymentJob po);

    /**
     * deploymentRecord 关联实体、deleted 软删除标记、targetFileRecordId 目标产物 Id
     * 在 VO 中均无对应来源,反向映射无源可取,忽略以免 unmapped 告警。
     */
    @Override
    @Mapping(target = "deploymentRecord", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "targetFileRecordId", ignore = true)
    DeploymentJob voToPo(DeploymentJobVo vo);
}
