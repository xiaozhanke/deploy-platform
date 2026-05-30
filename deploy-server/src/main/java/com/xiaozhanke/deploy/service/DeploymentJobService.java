package com.xiaozhanke.deploy.service;

import com.xiaozhanke.deploy.enums.JobStatusEnum;
import com.xiaozhanke.deploy.enums.JobTypeEnum;
import com.xiaozhanke.deploy.exception.BusinessException;
import com.xiaozhanke.deploy.exception.InvalidOperationException;
import com.xiaozhanke.deploy.exception.ResourceNotFoundException;
import com.xiaozhanke.deploy.messaging.producer.DeploymentMQProducer;
import com.xiaozhanke.deploy.model.entity.DeploymentJob;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

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

    /**
     * 创建部署作业
     */
    public DeploymentJobVo createJob(String deploymentRecordId, CreateJobRequest request) {
        DeploymentRecord record = deploymentRecordRepository.findById(deploymentRecordId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("部署记录 [%s] 不存在", deploymentRecordId)));

        // Phase 2 暂不支持 UPDATE(涉及文件传输与残留清理),提前拦截避免消费端反复失败
        if (request.getJobType() == JobTypeEnum.UPDATE) {
            throw new InvalidOperationException("作业类型 UPDATE 暂未启用");
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
        // 提前生成 jobId:事务消息半提交时消息体已序列化,若留给 JPA 延迟生成,broker 上的
        // payload 与 header 中 jobId 会被写成 null,导致回查与消费均无法定位记录
        String jobId = UUID.randomUUID().toString();
        DeploymentJob pending = new DeploymentJob()
                .setId(jobId)
                .setDeploymentRecord(record)
                .setJobType(request.getJobType())
                .setStatus(JobStatusEnum.PENDING)
                .setClientRequestId(request.getClientRequestId())
                .setRetryCount(0);

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
