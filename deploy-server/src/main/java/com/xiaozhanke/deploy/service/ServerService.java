package com.xiaozhanke.deploy.service;

import com.xiaozhanke.deploy.exception.ResourceNotFoundException;
import com.xiaozhanke.deploy.model.dto.ServerRecordDto;
import com.xiaozhanke.deploy.model.entity.ServerRecord;
import com.xiaozhanke.deploy.model.mapper.ServerPoDtoMapper;
import com.xiaozhanke.deploy.model.mapper.ServerPoVoMapper;
import com.xiaozhanke.deploy.model.request.ServerParams;
import com.xiaozhanke.deploy.model.request.ServerQueryParams;
import com.xiaozhanke.deploy.model.response.PageResult;
import com.xiaozhanke.deploy.model.vo.ServerRecordVo;
import com.xiaozhanke.deploy.repository.ServerRepository;
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
 * 服务器信息服务类
 *
 * @author xiaozhanke
 */
@Slf4j
@Service
public class ServerService {

    private final ServerRepository serverRepository;
    private final SshService sshService;
    private final ServerPoVoMapper serverPoVoMapper;
    private final ServerPoDtoMapper serverPoDtoMapper;

    public ServerService(ServerRepository serverRepository, SshService sshService, ServerPoVoMapper serverPoVoMapper,
                         ServerPoDtoMapper serverPoDtoMapper) {
        this.serverRepository = serverRepository;
        this.sshService = sshService;
        this.serverPoVoMapper = serverPoVoMapper;
        this.serverPoDtoMapper = serverPoDtoMapper;
    }

    /**
     * 添加服务器
     *
     * @param params 服务器信息参数
     * @return 保存的服务器信息
     */
    @Transactional
    public ServerRecordVo addServer(ServerParams params) {
        // 验证服务器信息
        setDefaultValues(params);
        ServerRecord serverRecord = new ServerRecord();
        BeanUtils.copyProperties(params, serverRecord);
        ServerRecord saved = serverRepository.save(serverRecord);
        return serverPoVoMapper.poToVo(saved);
    }

    /**
     * 更新服务器
     *
     * @param id     服务器 Id
     * @param params 服务器信息
     * @return 保存的服务器信息
     */
    @Transactional
    public ServerRecordVo updateServer(String id, ServerParams params) {
        // 验证服务器信息
        setDefaultValues(params);
        ServerRecord serverRecord = getServer(id);
        BeanUtils.copyProperties(params, serverRecord);
        ServerRecord saved = serverRepository.save(serverRecord);
        return serverPoVoMapper.poToVo(saved);
    }

    /**
     * 删除服务器
     *
     * @param id 服务器 Id
     */
    @Transactional
    public void deleteServer(String id) {
        ServerRecord server = getServer(id);
        server.setDeleted(true);
        serverRepository.save(server);
    }

    /**
     * 获取服务器 PO
     *
     * @param id 服务器 Id
     * @return 服务器 PO
     */
    public ServerRecord getServer(String id) {
        return serverRepository.findByIdAndDeletedIsFalse(id).orElseThrow(() -> new ResourceNotFoundException(String.format("服务器记录 [%s] 不存在", id)));
    }

    /**
     * 仅作为 FK 占位返回服务器代理。
     *
     * <p>用于 DeploymentService 拼装关联实体的场景：调用方只需要 FK 引用而不读取连接凭据，使用代理避免无谓地
     * 把 password / privateKey 等敏感字段拉到内存。先 {@link ServerRepository#existsByIdAndDeletedIsFalse}
     * 校验存在性以便给出 404，再用 {@link JpaRepository#getReferenceById} 取仅含 ID 的代理。
     *
     * @param id 服务器 Id
     * @return 仅持有 ID 的 ServerRecord 代理（必须在事务内访问其他字段才会触发懒加载）
     */
    public ServerRecord getServerReference(String id) {
        if (!serverRepository.existsByIdAndDeletedIsFalse(id)) {
            throw new ResourceNotFoundException(String.format("服务器记录 [%s] 不存在", id));
        }
        return serverRepository.getReferenceById(id);
    }

    /**
     * 获取服务器 DTO
     *
     * @param id 服务器 Id
     * @return 服务器 DTO
     */
    public ServerRecordDto getServerDto(String id) {
        ServerRecord serverRecord = getServer(id);
        return serverPoDtoMapper.poToDto(serverRecord);
    }

    /**
     * 查询服务器
     *
     * @param id 服务器 Id
     * @return 服务器 VO
     */
    public ServerRecordVo queryServer(String id) {
        ServerRecord serverRecord = getServer(id);
        return serverPoVoMapper.poToVo(serverRecord);
    }

    /**
     * 查询服务器列表
     *
     * @return 服务器列表
     */
    public List<ServerRecordVo> queryList() {
        List<ServerRecord> serverRecordList = serverRepository.findAllByDeletedIsFalse();
        return serverPoVoMapper.poListToVoList(serverRecordList);
    }

    /**
     * 分页查询服务器列表
     *
     * @param params   查询参数
     * @param pageable 分页参数
     * @return 分页结果
     */
    public PageResult<ServerRecordVo> queryPage(ServerQueryParams params, Pageable pageable) {
        Specification<ServerRecord> specification = buildSpecification(params);
        Page<ServerRecord> page = serverRepository.findAll(specification, pageable);
        List<ServerRecordVo> serverRecordList = serverPoVoMapper.poListToVoList(page.getContent());
        return new PageResult<>(serverRecordList, pageable, page.getTotalElements());
    }

    /**
     * 测试连接
     *
     * @param params 服务器信息参数
     * @return 连通结果
     */
    public boolean testConnection(ServerParams params) {
        return sshService.testConnection(params);
    }

    /**
     * 设置默认参数值
     *
     * @param params 服务器信息参数
     */
    private void setDefaultValues(ServerParams params) {
        if (!StringUtils.hasText(params.getName())) {
            params.setName(params.getUsername() + "@" + params.getHost());
        }

        if (!StringUtils.hasText(params.getHomeDir())) {
            params.setHomeDir("/home/" + params.getUsername());
        }
    }

    /**
     * 构建动态查询条件
     *
     * <p>仅对 name / host 做模糊匹配（like），并强制过滤软删记录；查询条件均为可选，
     * 任一为空则跳过对应谓词。
     *
     * @param params 服务器查询参数
     * @return 查询条件
     */
    private Specification<ServerRecord> buildSpecification(ServerQueryParams params) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicateList = new ArrayList<>();

            if (StringUtils.hasText(params.name())) {
                predicateList.add(criteriaBuilder.like(root.get("name"), "%" + params.name() + "%"));
            }

            if (StringUtils.hasText(params.host())) {
                predicateList.add(criteriaBuilder.like(root.get("host"), "%" + params.host() + "%"));
            }

            predicateList.add(criteriaBuilder.equal(root.get("deleted"), false));

            return criteriaBuilder.and(predicateList.toArray(new Predicate[0]));
        };
    }

} 