package com.xiaozhanke.deploy.service;

import com.xiaozhanke.deploy.exception.InvalidOperationException;
import com.xiaozhanke.deploy.exception.ResourceNotFoundException;
import com.xiaozhanke.deploy.model.entity.DeadLetterMessage;
import com.xiaozhanke.deploy.model.mapper.DeadLetterMessagePoVoMapper;
import com.xiaozhanke.deploy.model.request.CreateJobRequest;
import com.xiaozhanke.deploy.model.response.PageResult;
import com.xiaozhanke.deploy.model.vo.DeadLetterMessageVo;
import com.xiaozhanke.deploy.model.vo.DeploymentJobVo;
import com.xiaozhanke.deploy.repository.DeadLetterMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 死信查询与人工重试服务。
 *
 * <p>人工重试的语义是**新建一份新 jobId 的作业**(走 {@link DeploymentJobService#createJob} HTTP 入口),
 * **不**是把死信消息复投回原 Topic——后者会破坏顺序消息的串行保证。
 *
 * @author xiaozhanke
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterService {

    private final DeadLetterMessageRepository deadLetterMessageRepository;
    private final DeadLetterMessagePoVoMapper deadLetterMessagePoVoMapper;
    private final DeploymentJobService deploymentJobService;

    /**
     * 分页查询死信,可按是否已重试过滤。
     */
    public PageResult<DeadLetterMessageVo> queryDeadLetters(Boolean retried, Pageable pageable) {
        Specification<DeadLetterMessage> specification = (root, query, criteriaBuilder) -> {
            if (retried == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("retried"), retried);
        };
        Page<DeadLetterMessage> page = deadLetterMessageRepository.findAll(specification, pageable);
        return new PageResult<>(deadLetterMessagePoVoMapper.poListToVoList(page.getContent()), pageable,
                page.getTotalElements());
    }

    /**
     * 获取单条死信。
     */
    public DeadLetterMessageVo getDeadLetter(String id) {
        return deadLetterMessagePoVoMapper.poToVo(load(id));
    }

    /**
     * 人工重试:用死信里的 (recordId, jobType) 新建一份新 jobId 的作业,并标记该死信已重试。
     */
    @Transactional
    public DeploymentJobVo retry(String id) {
        DeadLetterMessage deadLetter = load(id);
        if (Boolean.TRUE.equals(deadLetter.getRetried())) {
            throw new InvalidOperationException("该死信已重试,请勿重复操作");
        }
        CreateJobRequest request = new CreateJobRequest();
        request.setJobType(deadLetter.getJobType());
        request.setClientRequestId(UUID.randomUUID().toString());
        DeploymentJobVo newJob = deploymentJobService.createJob(deadLetter.getDeploymentRecordId(), request);

        deadLetter.setRetried(true).setRetriedJobId(newJob.getId());
        deadLetterMessageRepository.save(deadLetter);
        log.info("死信 [{}] 人工重试,新建作业 jobId=[{}]", id, newJob.getId());
        return newJob;
    }

    private DeadLetterMessage load(String id) {
        return deadLetterMessageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("死信记录 [%s] 不存在", id)));
    }
}
