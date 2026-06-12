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
 * @author xiaozhanke
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DeploymentRecordPoVoMapper extends BasePoVoMapper<DeploymentRecord, DeploymentRecordVo> {

    /**
     * 最近作业由 service 查询后单独回填(实体上无对应字段),映射时忽略以免 unmapped 告警。
     */
    @Override
    @Mapping(target = "latestJob", ignore = true)
    DeploymentRecordVo poToVo(DeploymentRecord po);
}
