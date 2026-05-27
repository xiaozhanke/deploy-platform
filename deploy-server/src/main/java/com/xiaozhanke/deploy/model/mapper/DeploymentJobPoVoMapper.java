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

    @Override
    @Mapping(target = "deploymentRecord", ignore = true)
    DeploymentJob voToPo(DeploymentJobVo vo);
}
