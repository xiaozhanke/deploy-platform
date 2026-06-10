package com.xiaozhanke.deploy.service;

import com.xiaozhanke.deploy.exception.ResourceNotFoundException;
import com.xiaozhanke.deploy.model.dto.HostRecordDto;
import com.xiaozhanke.deploy.model.entity.HostRecord;
import com.xiaozhanke.deploy.model.mapper.HostPoDtoMapper;
import com.xiaozhanke.deploy.model.mapper.HostPoVoMapper;
import com.xiaozhanke.deploy.model.request.HostParams;
import com.xiaozhanke.deploy.model.request.HostQueryParams;
import com.xiaozhanke.deploy.model.response.PageResult;
import com.xiaozhanke.deploy.model.vo.HostRecordVo;
import com.xiaozhanke.deploy.repository.HostRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 主机信息服务类
 *
 * @author xiaozhanke
 */
@Slf4j
@Service
public class HostService {

    private final HostRepository hostRepository;
    private final SshService sshService;
    private final HostPoVoMapper hostPoVoMapper;
    private final HostPoDtoMapper hostPoDtoMapper;

    public HostService(HostRepository hostRepository, SshService sshService, HostPoVoMapper hostPoVoMapper,
                       HostPoDtoMapper hostPoDtoMapper) {
        this.hostRepository = hostRepository;
        this.sshService = sshService;
        this.hostPoVoMapper = hostPoVoMapper;
        this.hostPoDtoMapper = hostPoDtoMapper;
    }

    /**
     * 添加主机
     *
     * @param params 主机信息参数
     * @return 保存的主机信息
     */
    @Transactional
    public HostRecordVo addHost(HostParams params) {
        // 验证主机信息
        setDefaultValues(params);
        HostRecord hostRecord = new HostRecord();
        BeanUtils.copyProperties(params, hostRecord);
        HostRecord saved = hostRepository.save(hostRecord);
        return hostPoVoMapper.poToVo(saved);
    }

    /**
     * 更新主机
     *
     * @param id     主机 Id
     * @param params 主机信息
     * @return 保存的主机信息
     */
    @Transactional
    public HostRecordVo updateHost(String id, HostParams params) {
        // 验证主机信息
        setDefaultValues(params);
        HostRecord hostRecord = getHost(id);
        BeanUtils.copyProperties(params, hostRecord);
        HostRecord saved = hostRepository.save(hostRecord);
        return hostPoVoMapper.poToVo(saved);
    }

    /**
     * 删除主机
     *
     * @param id 主机 Id
     */
    @Transactional
    public void deleteHost(String id) {
        HostRecord host = getHost(id);
        host.setDeleted(true);
        hostRepository.save(host);
    }

    /**
     * 获取主机 PO
     *
     * @param id 主机 Id
     * @return 主机 PO
     */
    public HostRecord getHost(String id) {
        return hostRepository.findByIdAndDeletedIsFalse(id).orElseThrow(() -> new ResourceNotFoundException(String.format("主机记录 [%s] 不存在", id)));
    }

    /**
     * 仅作为 FK 占位返回主机代理。
     *
     * <p>用于 DeploymentService 拼装关联实体的场景：调用方只需要 FK 引用而不读取连接凭据，使用代理避免无谓地
     * 把 password / privateKey 等敏感字段拉到内存。先 {@link HostRepository#existsByIdAndDeletedIsFalse}
     * 校验存在性以便给出 404，再用 {@link JpaRepository#getReferenceById} 取仅含 ID 的代理。
     *
     * @param id 主机 Id
     * @return 仅持有 ID 的 HostRecord 代理（必须在事务内访问其他字段才会触发懒加载）
     */
    public HostRecord getHostReference(String id) {
        if (!hostRepository.existsByIdAndDeletedIsFalse(id)) {
            throw new ResourceNotFoundException(String.format("主机记录 [%s] 不存在", id));
        }
        return hostRepository.getReferenceById(id);
    }

    /**
     * 获取主机 DTO
     *
     * @param id 主机 Id
     * @return 主机 DTO
     */
    public HostRecordDto getHostDto(String id) {
        HostRecord hostRecord = getHost(id);
        return hostPoDtoMapper.poToDto(hostRecord);
    }

    /**
     * 查询主机
     *
     * @param id 主机 Id
     * @return 主机 VO
     */
    public HostRecordVo queryHost(String id) {
        HostRecord hostRecord = getHost(id);
        return hostPoVoMapper.poToVo(hostRecord);
    }

    /**
     * 查询主机列表
     *
     * @return 主机列表
     */
    public List<HostRecordVo> queryList() {
        List<HostRecord> hostRecordList = hostRepository.findAllByDeletedIsFalse();
        return hostPoVoMapper.poListToVoList(hostRecordList);
    }

    /**
     * 分页查询主机列表
     *
     * @param params   查询参数
     * @param pageable 分页参数
     * @return 分页结果
     */
    public PageResult<HostRecordVo> queryPage(HostQueryParams params, Pageable pageable) {
        Specification<HostRecord> specification = buildSpecification(params);
        Page<HostRecord> page = hostRepository.findAll(specification, pageable);
        List<HostRecordVo> hostRecordList = hostPoVoMapper.poListToVoList(page.getContent());
        return new PageResult<>(hostRecordList, pageable, page.getTotalElements());
    }

    /**
     * 测试连接
     *
     * @param params 主机信息参数
     * @return 连通结果
     */
    public boolean testConnection(HostParams params) {
        return sshService.testConnection(params);
    }

    /**
     * 设置默认参数值
     *
     * @param params 主机信息参数
     */
    private void setDefaultValues(HostParams params) {
        if (!StringUtils.hasText(params.getName())) {
            params.setName(params.getUsername() + "@" + params.getAddress());
        }

        if (!StringUtils.hasText(params.getHomeDir())) {
            params.setHomeDir("/home/" + params.getUsername());
        }
    }

    /**
     * 构建动态查询条件
     *
     * <p>仅对 name / address 做模糊匹配（like），并强制过滤软删记录；查询条件均为可选，
     * 任一为空则跳过对应谓词。
     *
     * @param params 主机查询参数
     * @return 查询条件
     */
    private Specification<HostRecord> buildSpecification(HostQueryParams params) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicateList = new ArrayList<>();

            if (StringUtils.hasText(params.name())) {
                predicateList.add(criteriaBuilder.like(root.get("name"), "%" + params.name() + "%"));
            }

            if (StringUtils.hasText(params.address())) {
                predicateList.add(criteriaBuilder.like(root.get("address"), "%" + params.address() + "%"));
            }

            predicateList.add(criteriaBuilder.equal(root.get("deleted"), false));

            return criteriaBuilder.and(predicateList.toArray(new Predicate[0]));
        };
    }

}
