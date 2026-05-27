package com.xiaozhanke.deploy.messaging.transaction;

import com.xiaozhanke.deploy.model.entity.DeploymentJob;
import com.xiaozhanke.deploy.repository.DeploymentJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;

/**
 * RocketMQ 事务消息本地事务回调与回查(对应 MQ 方案稿场景 1、CONTEXT.md「本地事务」)。
 *
 * <p>本地事务**仅**做一行 {@code INSERT INTO deployment_job},SSH 远程命令由 consumer
 * 在拿到消息后执行,**不**在本地事务内——本地事务必须秒级可判定 commit/rollback,否则破坏回查机制。
 *
 * <p>arg 透传:producer 调 {@code rocketMQTemplate.sendMessageInTransaction(topic, msg, arg)}
 * 时传入待入库的 {@link DeploymentJob},listener 在事务内 save 即可。
 *
 * @author xiaozhanke
 */
@Slf4j
@RocketMQTransactionListener
@RequiredArgsConstructor
public class DeploymentTransactionListener implements RocketMQLocalTransactionListener {

    private final DeploymentJobRepository deploymentJobRepository;

    @Override
    @Transactional
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        if (!(arg instanceof DeploymentJob pendingJob)) {
            log.warn("executeLocalTransaction 收到非预期的 arg 类型: {}", arg);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        try {
            deploymentJobRepository.save(pendingJob);
            log.info("本地事务 INSERT deployment_job [{}] 成功,提交事务消息", pendingJob.getId());
            return RocketMQLocalTransactionState.COMMIT;
        } catch (DataIntegrityViolationException e) {
            // (record_id, job_type, client_request_id) 唯一索引冲突:前一次请求已写入,半消息回滚
            log.warn("本地事务唯一索引冲突,作业已存在,回滚事务消息: {}", e.getMessage());
            return RocketMQLocalTransactionState.ROLLBACK;
        } catch (Exception e) {
            log.error("本地事务 INSERT 失败,回滚事务消息", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        Object jobIdHeader = msg.getHeaders().get("jobId");
        if (jobIdHeader == null) {
            log.warn("checkLocalTransaction 收到无 jobId header 的消息,回滚");
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        String jobId = jobIdHeader.toString();
        boolean exists = deploymentJobRepository.existsById(jobId);
        log.info("事务消息回查 jobId=[{}] exists=[{}]", jobId, exists);
        return exists ? RocketMQLocalTransactionState.COMMIT : RocketMQLocalTransactionState.ROLLBACK;
    }
}
