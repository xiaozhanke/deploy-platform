package com.xiaozhanke.deploy.service;

import com.xiaozhanke.deploy.enums.ApplicationTypeEnum;
import com.xiaozhanke.deploy.exception.BusinessException;
import com.xiaozhanke.deploy.exception.ResourceNotFoundException;
import com.xiaozhanke.deploy.model.dto.HostRecordDto;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import com.xiaozhanke.deploy.model.entity.FileRecord;
import com.xiaozhanke.deploy.model.entity.HostRecord;
import com.xiaozhanke.deploy.model.mapper.DeploymentJobPoVoMapper;
import com.xiaozhanke.deploy.model.mapper.DeploymentRecordPoVoMapper;
import com.xiaozhanke.deploy.model.request.DeploymentParams;
import com.xiaozhanke.deploy.model.response.PageResult;
import com.xiaozhanke.deploy.model.vo.DeploymentJobVo;
import com.xiaozhanke.deploy.model.vo.DeploymentRecordVo;
import com.xiaozhanke.deploy.monitor.LivenessCache;
import com.xiaozhanke.deploy.repository.DeploymentJobRepository;
import com.xiaozhanke.deploy.repository.DeploymentRecordRepository;
import com.xiaozhanke.deploy.util.ShellArgEscaper;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 部署服务类
 *
 * @author xiaozhanke
 */
@Slf4j
@Service
public class DeploymentService {

    private final DeploymentRecordRepository deploymentRecordRepository;
    private final DeploymentRecordPoVoMapper deploymentRecordPoVoMapper;
    private final DeploymentJobRepository deploymentJobRepository;
    private final DeploymentJobPoVoMapper deploymentJobPoVoMapper;
    private final SshService sshService;
    private final HostService hostService;
    private final FileStorageService fileStorageService;
    private final LivenessCache livenessCache;

    public DeploymentService(DeploymentRecordRepository deploymentRecordRepository,
                             DeploymentRecordPoVoMapper deploymentRecordPoVoMapper,
                             DeploymentJobRepository deploymentJobRepository,
                             DeploymentJobPoVoMapper deploymentJobPoVoMapper,
                             SshService sshService,
                             HostService hostService,
                             FileStorageService fileStorageService,
                             LivenessCache livenessCache) {
        this.deploymentRecordRepository = deploymentRecordRepository;
        this.deploymentRecordPoVoMapper = deploymentRecordPoVoMapper;
        this.deploymentJobRepository = deploymentJobRepository;
        this.deploymentJobPoVoMapper = deploymentJobPoVoMapper;
        this.sshService = sshService;
        this.hostService = hostService;
        this.fileStorageService = fileStorageService;
        this.livenessCache = livenessCache;
    }

    /**
     * 创建部署记录
     *
     * @param params 部署参数
     * @return 保存后的部署记录信息
     */
    @Transactional
    public DeploymentRecordVo createDeployment(DeploymentParams params) {
        // 关联仅作 FK 占位：用代理避免把 HostRecord/FileRecord 的全部字段拉到内存
        HostRecord hostRecord = hostService.getHostReference(params.getHostRecordId());
        FileRecord fileRecord = fileStorageService.getFileRecordReference(params.getFileRecordId());

        DeploymentRecord deployment = new DeploymentRecord()
                .setHostRecord(hostRecord)
                .setFileRecord(fileRecord)
                .setApplicationType(params.getApplicationType())
                .setDeploymentPath(params.getDeploymentPath())
                .setDeploymentConfigPath(params.getDeploymentConfigPath())
                .setPort(params.getPort())
                .setProgramArgs(params.getProgramArgs())
                .setActiveProfiles(params.getActiveProfiles())
                .setStatus(params.getStatus())
                .setErrorMessage(params.getErrorMessage())
                .setDeployTime(LocalDateTime.now())
                .setLastStartTime(params.getLastStartTime())
                .setLastStopTime(params.getLastStopTime())
                .setProcessId(params.getProcessId())
                .setRunning(params.getRunning());
        DeploymentRecord saved = deploymentRecordRepository.save(deployment);
        return deploymentRecordPoVoMapper.poToVo(saved);
    }


    /**
     * 查询部署记录列表
     *
     * @param params 查询参数
     * @param sort   排序参数
     * @return 部署记录列表
     */
    public List<DeploymentRecordVo> queryList(DeploymentParams params, Sort sort) {
        Specification<DeploymentRecord> specification = buildSpecification(params);
        List<DeploymentRecordVo> deploymentList =
                deploymentRecordPoVoMapper.poListToVoList(deploymentRecordRepository.findAll(specification, sort));
        populateLivenessState(deploymentList);
        return deploymentList;
    }

    /**
     * 分页查询部署记录列表
     *
     * @param params   查询参数
     * @param pageable 分页参数
     * @return 分页结果
     */
    public PageResult<DeploymentRecordVo> queryPage(DeploymentParams params, Pageable pageable) {
        Specification<DeploymentRecord> specification = buildSpecification(params);
        Page<DeploymentRecord> page = deploymentRecordRepository.findAll(specification, pageable);
        List<DeploymentRecordVo> deploymentList = deploymentRecordPoVoMapper.poListToVoList(page.getContent());
        populateLatestJobs(deploymentList);
        populateLivenessState(deploymentList);
        return new PageResult<>(deploymentList, pageable, page.getTotalElements());
    }

    /**
     * 为部署记录 VO 列表回填存活三态：读取时由 {@code (running, processId, 存活探测缓存)} 派生，不落库。
     * 使列表「是否运行中」列反映真实进程探测结果而非仅运行意图——意图为 running 但进程已崩溃时派生为「已停止」，
     * 缺少 PID 或尚无探测结果时派生为「状态未知」。
     */
    private void populateLivenessState(List<DeploymentRecordVo> deploymentList) {
        deploymentList.forEach(record -> record.setLivenessState(
                livenessCache.resolveInstanceState(record.getRunning(), record.getProcessId(), record.getId())));
    }

    /**
     * 为当前页每条部署记录回填「最近一次作业」。
     *
     * <p>前端列表「最近作业」列若仅靠「提交时乐观写入 + WebSocket 实时推送」,刷新或重进页面后会全部回落成空。
     * 此处一次性批量查出整页记录各自最新的作业,作为权威初值下发,使该列在刷新后也能持久展示。
     */
    private void populateLatestJobs(List<DeploymentRecordVo> deploymentList) {
        if (deploymentList.isEmpty()) {
            return;
        }
        List<String> recordIds = deploymentList.stream().map(DeploymentRecordVo::getId).toList();
        Map<String, DeploymentJobVo> latestJobByRecordId = deploymentJobRepository
                .findLatestByDeploymentRecordIdIn(recordIds).stream()
                .map(deploymentJobPoVoMapper::poToVo)
                // 同一记录极端并列时按先到者去重,避免 toMap 抛 IllegalStateException
                .collect(Collectors.toMap(DeploymentJobVo::getDeploymentRecordId, vo -> vo, (first, second) -> first));
        deploymentList.forEach(record -> record.setLatestJob(latestJobByRecordId.get(record.getId())));
    }

    /**
     * 查询部署记录
     *
     * @param id 部署记录 Id
     * @return 部署记录信息
     */
    public DeploymentRecordVo queryDeployment(String id) {
        DeploymentRecord deployment = getDeployment(id);
        DeploymentRecordVo vo = deploymentRecordPoVoMapper.poToVo(deployment);
        vo.setLivenessState(livenessCache.resolveInstanceState(
                vo.getRunning(), vo.getProcessId(), vo.getId()));
        return vo;
    }

    /**
     * 更新部署记录
     *
     * @param id     部署记录 Id
     * @param params 部署参数
     * @return 更新后的部署记录信息
     */
    @Transactional
    public DeploymentRecordVo updateDeployment(String id, DeploymentParams params) {
        DeploymentRecord deployment = getDeployment(id);
        HostRecord hostRecord = hostService.getHostReference(params.getHostRecordId());
        FileRecord fileRecord = fileStorageService.getFileRecordReference(params.getFileRecordId());

        deployment.setHostRecord(hostRecord)
                .setFileRecord(fileRecord)
                .setApplicationType(params.getApplicationType())
                .setDeploymentPath(params.getDeploymentPath())
                .setDeploymentConfigPath(params.getDeploymentConfigPath())
                .setPort(params.getPort())
                .setProgramArgs(params.getProgramArgs())
                .setActiveProfiles(params.getActiveProfiles())
                .setStatus(params.getStatus())
                .setErrorMessage(params.getErrorMessage())
                .setLastStartTime(params.getLastStartTime())
                .setLastStopTime(params.getLastStopTime())
                .setProcessId(params.getProcessId())
                .setRunning(params.getRunning());
        DeploymentRecord updated = deploymentRecordRepository.save(deployment);
        return deploymentRecordPoVoMapper.poToVo(updated);
    }

    /**
     * 删除部署记录
     *
     * @param id 部署记录 Id
     */
    @Transactional
    public void deleteDeployment(String id) {
        DeploymentRecord deployment = getDeployment(id);
        deploymentRecordRepository.delete(deployment);
    }

    /**
     * 获取部署记录 PO
     *
     * @param id 部署记录 Id
     * @return 部署记录 PO
     */
    private DeploymentRecord getDeployment(String id) {
        return deploymentRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("部署记录 [%s] 不存在", id)));
    }

    /**
     * 构建复杂查询参数
     *
     * @param params 查询参数
     * @return 复杂查询参数
     */
    private Specification<DeploymentRecord> buildSpecification(DeploymentParams params) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(params.getHostRecordId())) {
                predicates.add(criteriaBuilder.equal(root.get("hostRecord").get("id"), params.getHostRecordId()));
            }

            if (StringUtils.hasText(params.getFileRecordId())) {
                predicates.add(criteriaBuilder.equal(root.get("fileRecord").get("id"), params.getFileRecordId()));
            }

            if (params.getApplicationType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("applicationType"), params.getApplicationType()));
            }

            if (StringUtils.hasText(params.getDeploymentPath())) {
                predicates.add(criteriaBuilder.like(root.get("deploymentPath"), "%" + params.getDeploymentPath() +
                        "%"));
            }

            if (params.getPort() != null) {
                predicates.add(criteriaBuilder.equal(root.get("port"), params.getPort()));
            }

            if (StringUtils.hasText(params.getActiveProfiles())) {
                predicates.add(criteriaBuilder.like(root.get("activeProfiles"), "%" + params.getActiveProfiles() +
                        "%"));
            }

            if (params.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), params.getStatus()));
            }

            if (params.getRunning() != null) {
                predicates.add(criteriaBuilder.equal(root.get("running"), params.getRunning()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 获取应用状态
     *
     * @param id 部署记录 Id
     * @return 更新后的部署记录信息
     */
    public DeploymentRecordVo getApplicationStatus(String id) {
        DeploymentRecord deployment = getDeployment(id);

        // 检查应用类型
        if (deployment.getApplicationType() != ApplicationTypeEnum.BACKEND) {
            throw new BusinessException("只有后端应用才能查询状态");
        }

        // 检查应用是否在运行
        if (!Boolean.TRUE.equals(deployment.getRunning())) {
            return deploymentRecordPoVoMapper.poToVo(deployment);
        }

        try {
            // 获取主机信息
            HostRecordDto host = hostService.getHostDto(deployment.getHostRecord().getId());

            // processId 强制数字，避免被 shell 当 jobspec 或被注入额外语义
            String command = String.format("ps -p %s > /dev/null && echo 'running' || echo 'stopped'",
                    ShellArgEscaper.requireNumericProcessId(deployment.getProcessId()));

            // 执行检查命令
            String result = sshService.executeCommand(host, command);

            // 更新运行状态
            boolean isRunning = "running".equals(result.trim());
            if (isRunning != deployment.getRunning()) {
                deployment.setRunning(isRunning)
                        .setLastStopTime(isRunning ? null : LocalDateTime.now());
                deployment = deploymentRecordRepository.save(deployment);
            }

            return deploymentRecordPoVoMapper.poToVo(deployment);
        } catch (Exception e) {
            throw new BusinessException(String.format("获取应用状态失败: %s", e.getMessage()), e);
        }
    }

}
