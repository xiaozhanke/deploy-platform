package com.xiaozhanke.deploy.service;

import com.xiaozhanke.deploy.enums.ApplicationTypeEnum;
import com.xiaozhanke.deploy.enums.JobStatusEnum;
import com.xiaozhanke.deploy.enums.JobTypeEnum;
import com.xiaozhanke.deploy.exception.BusinessException;
import com.xiaozhanke.deploy.exception.InvalidOperationException;
import com.xiaozhanke.deploy.exception.ResourceNotFoundException;
import com.xiaozhanke.deploy.messaging.producer.DeploymentDelayedProducer;
import com.xiaozhanke.deploy.messaging.producer.DeploymentMQProducer;
import com.xiaozhanke.deploy.model.entity.DeploymentJob;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import com.xiaozhanke.deploy.model.entity.FileRecord;
import com.xiaozhanke.deploy.model.mapper.DeploymentJobPoVoMapper;
import com.xiaozhanke.deploy.model.request.CreateJobRequest;
import com.xiaozhanke.deploy.model.response.PageResult;
import com.xiaozhanke.deploy.model.vo.DeploymentJobVo;
import com.xiaozhanke.deploy.repository.DeploymentJobRepository;
import com.xiaozhanke.deploy.repository.DeploymentRecordRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 部署作业服务类
 *
 * <p>负责"创建部署作业"的完整入口流程:
 * <ol>
 *   <li>HTTP 入口防重——按 (recordId, jobType, clientRequestId) 三元组查重,命中则直接返回已存在作业
 *   <li>未命中 → 调 {@link DeploymentMQProducer#sendDeploymentJob} 发事务消息,
 *       本地事务由 {@code DeploymentTransactionListener} 在 RocketMQ 半消息提交回调里完成
 *   <li>并发竞争触发的 {@link org.springframework.dao.DataIntegrityViolationException} 在 listener 内
 *       捕获并 ROLLBACK 半消息;此处兜底再 SELECT 一次拿已存在 jobId 返回
 * </ol>
 *
 * @author xiaozhanke
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentJobService {

    private final DeploymentJobRepository deploymentJobRepository;
    private final DeploymentRecordRepository deploymentRecordRepository;
    private final DeploymentJobPoVoMapper deploymentJobPoVoMapper;
    private final DeploymentMQProducer deploymentMQProducer;
    private final DeploymentDelayedProducer deploymentDelayedProducer;
    private final FileStorageService fileStorageService;

    /**
     * 创建部署作业
     */
    public DeploymentJobVo createJob(String deploymentRecordId, CreateJobRequest request) {
        DeploymentRecord record = deploymentRecordRepository.findById(deploymentRecordId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("部署记录 [%s] 不存在", deploymentRecordId)));

        // UPDATE 作业:校验目标包存在且扩展名与应用类型匹配(后端 .jar / 前端 .zip),
        // 在 HTTP 入口提前拦截非法请求,避免消费端反复失败进死信
        if (request.getJobType() == JobTypeEnum.UPDATE) {
            validateUpdateTarget(record, request.getFileRecordId());
        }

        // 第二道防重:HTTP 入口按 (recordId, jobType, clientRequestId) 查已存在作业
        return deploymentJobRepository.findByDeploymentRecordIdAndJobTypeAndClientRequestId(
                        deploymentRecordId, request.getJobType(), request.getClientRequestId())
                .map(existing -> {
                    log.info("重复请求命中已有作业,直接返回: jobId=[{}] clientRequestId=[{}]",
                            existing.getId(), request.getClientRequestId());
                    return deploymentJobPoVoMapper.poToVo(existing);
                })
                .orElseGet(() -> doCreateJob(record, request));
    }

    private DeploymentJobVo doCreateJob(DeploymentRecord record, CreateJobRequest request) {
        // 延迟作业走独立流程:直接 INSERT + 发延迟消息(不走事务消息)
        if (request.getExecuteAt() != null) {
            return createDelayedJob(record, request);
        }

        // 提前生成 jobId:事务消息半提交时消息体已序列化,若留给 JPA 延迟生成,broker 上的
        // payload 与 header 中 jobId 会被写成 null,导致回查与消费均无法定位记录
        String jobId = UUID.randomUUID().toString();
        DeploymentJob pending = buildPendingJob(jobId, record, request);

        SendResult result = deploymentMQProducer.sendDeploymentJob(pending);
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            throw new BusinessException(String.format("事务消息半提交失败: jobId=[%s] status=[%s]",
                    jobId, result == null ? "null" : result.getSendStatus()));
        }

        // listener 已经把 pending 写入 DB 并 commit 半消息,此处直接转 VO 返回
        // 若 listener ROLLBACK(唯一索引冲突),再查一次拿已存在的兜底返回
        DeploymentJob persisted = deploymentJobRepository.findById(jobId)
                .or(() -> deploymentJobRepository.findByDeploymentRecordIdAndJobTypeAndClientRequestId(
                        record.getId(), request.getJobType(), request.getClientRequestId()))
                .orElseThrow(() -> new IllegalStateException(
                        String.format("作业创建失败: recordId=[%s] jobType=[%s] clientRequestId=[%s]",
                                record.getId(), request.getJobType(), request.getClientRequestId())));
        return deploymentJobPoVoMapper.poToVo(persisted);
    }

    /**
     * 创建延迟作业:直接 INSERT 入库 + 发延迟消息(不走事务消息,延迟消息不需要事务保证)。
     *
     * <p>延迟消息发失败的话,作业留在 PENDING 状态永不执行——这在作品集项目可接受;
     * 生产环境需要定时扫描过期 PENDING 作业做补偿。
     * 并发重复请求(同 clientRequestId)触发唯一索引冲突时,兜底返回已存在作业,
     * 与非延迟事务消息路径的 ROLLBACK+重查行为一致。
     */
    private DeploymentJobVo createDelayedJob(DeploymentRecord record, CreateJobRequest request) {
        LocalDateTime executeAt = request.getExecuteAt();
        LocalDateTime now = LocalDateTime.now();
        if (!executeAt.isAfter(now)) {
            throw new InvalidOperationException(String.format(
                    "执行时间 [%s] 必须晚于当前时间 [%s]", executeAt, now));
        }
        long delaySeconds = Duration.between(now, executeAt).getSeconds();
        if (delaySeconds <= 0) {
            throw new InvalidOperationException("延迟秒数必须大于 0");
        }

        String jobId = UUID.randomUUID().toString();
        DeploymentJob pending = buildPendingJob(jobId, record, request);
        // 延迟作业直接 INSERT;并发重复请求触发唯一索引冲突时,兜底返回已存在作业
        try {
            deploymentJobRepository.save(pending);
        } catch (DataIntegrityViolationException e) {
            log.info("延迟作业唯一索引冲突,返回已存在作业: clientRequestId=[{}]", request.getClientRequestId());
            DeploymentJob existing = deploymentJobRepository
                    .findByDeploymentRecordIdAndJobTypeAndClientRequestId(
                            record.getId(), request.getJobType(), request.getClientRequestId())
                    .orElseThrow(() -> new IllegalStateException(
                            "唯一索引冲突但查不到已存在作业: " + request.getClientRequestId()));
            return deploymentJobPoVoMapper.poToVo(existing);
        }

        try {
            SendResult result = deploymentDelayedProducer.sendDelayed(pending, executeAt, delaySeconds);
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                log.error("延迟消息发送失败,作业 [{}] 将保持在 PENDING 状态: status=[{}]",
                        jobId, result == null ? "null" : result.getSendStatus());
            }
        } catch (Exception e) {
            // syncSend 直接抛异常(如 MQClientException):作业已入库,记日志不阻断响应
            log.error("延迟消息发送异常,作业 [{}] 将保持在 PENDING 状态", jobId, e);
        }

        return deploymentJobPoVoMapper.poToVo(pending);
    }

    /**
     * 取消 PENDING 状态的作业。
     *
     * <p>CAS UPDATE:仅当作业仍为 PENDING 时才能转入 CANCELLED。已开始执行(IN_PROGRESS)
     * 的作业不可撤销——CAS 是撤销 HTTP 请求与消费端占据的最终仲裁。
     *
     * @param jobId 作业 Id
     * @return 更新后的作业 VO
     * @throws InvalidOperationException 作业已开始执行,无法取消
     * @throws ResourceNotFoundException 作业不存在
     */
    @Transactional
    public DeploymentJobVo cancelJob(String jobId) {
        // 加载作业(一次查询同时完成存在性校验与当前状态获取)
        DeploymentJob job = deploymentJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("部署作业 [%s] 不存在", jobId)));
        // CAS 撤销:PENDING 才能转入 CANCELLED;CAS 是撤销与消费端的最终仲裁
        int affected = deploymentJobRepository.cancelIfPending(jobId, LocalDateTime.now());
        if (affected == 0) {
            // CAS 不命中:作业已不是 PENDING(cancelIfPending 的 clearAutomatically 已清缓存,
            // 但 findById 加载的 job 仍在内存,其 status 可直接用于构造错误消息)
            throw new InvalidOperationException(String.format(
                    "作业 [%s] 当前状态为 [%s],只有 PENDING 状态的作业可以撤销",
                    jobId, job.getStatus().getDescription()));
        }
        log.info("作业 [{}] 已撤销(PENDING → CANCELLED)", jobId);
        // CAS 成功后,直接修改内存中的 job 状态为 CANCELLED 返回即可,
        // 无需重新查询(cancelIfPending 的 clearAutomatically 不改变此 job 引用)
        job.setStatus(JobStatusEnum.CANCELLED).setEndTime(LocalDateTime.now());
        return deploymentJobPoVoMapper.poToVo(job);
    }

    /**
     * 构造 PENDING 状态的 DeploymentJob(公共字段填充,消除 doCreateJob 与
     * createDelayedJob 之间的 6 字段重复构造)。
     */
    private DeploymentJob buildPendingJob(String jobId, DeploymentRecord record, CreateJobRequest request) {
        DeploymentJob job = new DeploymentJob()
                .setId(jobId)
                .setDeploymentRecord(record)
                .setJobType(request.getJobType())
                .setStatus(JobStatusEnum.PENDING)
                .setClientRequestId(request.getClientRequestId())
                .setRetryCount(0);
        // UPDATE 作业携带目标包引用,执行时据此换 fileRecord 指针;其余类型该字段保持 null
        if (request.getJobType() == JobTypeEnum.UPDATE) {
            job.setTargetFileRecordId(request.getFileRecordId());
        }
        return job;
    }

    /**
     * 校验 UPDATE 作业的目标包:必须指定、必须存在、扩展名与应用类型匹配
     * (后端 .jar / 前端 .zip)。
     */
    private void validateUpdateTarget(DeploymentRecord record, String fileRecordId) {
        if (fileRecordId == null || fileRecordId.isBlank()) {
            throw new InvalidOperationException("UPDATE 作业必须指定目标文件记录 Id");
        }
        FileRecord targetFile = fileStorageService.getFileRecord(fileRecordId);
        String fileName = targetFile.getFileName();
        if (record.getApplicationType() == ApplicationTypeEnum.BACKEND && !fileName.endsWith(".jar")) {
            throw new InvalidOperationException("更新失败: 后端应用只能更新 jar 包");
        }
        if (record.getApplicationType() == ApplicationTypeEnum.FRONTEND && !fileName.endsWith(".zip")) {
            throw new InvalidOperationException("更新失败: 前端应用只能更新 zip 包");
        }
    }

    /**
     * 获取单个作业
     */
    public DeploymentJobVo getJob(String jobId) {
        DeploymentJob job = deploymentJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("部署作业 [%s] 不存在", jobId)));
        return deploymentJobPoVoMapper.poToVo(job);
    }

    /**
     * 分页查询指定部署记录下的作业
     */
    public PageResult<DeploymentJobVo> queryJobs(String deploymentRecordId, JobStatusEnum status,
                                                 Pageable pageable) {
        Specification<DeploymentJob> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("deploymentRecord").get("id"), deploymentRecordId));
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<DeploymentJob> page = deploymentJobRepository.findAll(specification, pageable);
        List<DeploymentJobVo> content = deploymentJobPoVoMapper.poListToVoList(page.getContent());
        return new PageResult<>(content, pageable, page.getTotalElements());
    }
}
