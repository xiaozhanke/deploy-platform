package com.xiaozhanke.deploy.service;

import com.xiaozhanke.deploy.enums.DeploymentStatusEnum;
import com.xiaozhanke.deploy.enums.JobStatusEnum;
import com.xiaozhanke.deploy.model.entity.DeploymentJob;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import com.xiaozhanke.deploy.repository.DeploymentJobRepository;
import com.xiaozhanke.deploy.repository.DeploymentRecordRepository;
import com.xiaozhanke.deploy.util.ErrorMessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 部署作业执行结果的状态写入服务。
 *
 * <p>把"作业状态推进 + 部署记录运行态投影"的数据库写入从 {@code DeploymentConsumer} 抽到独立 bean,
 * 解决两个问题:
 * <ol>
 *   <li>原 consumer 把 {@code @Transactional} 标在被 {@code this.} 自调用的方法上,**代理不生效**;
 *       移到独立 bean 后经 Spring 代理调用,事务边界真正生效。</li>
 *   <li>SSH 远程命令(秒级~几十秒)**不**进事务——只有这里的状态写入是短事务,不长时间持有数据库连接。</li>
 * </ol>
 *
 * <p>所有方法以 {@code recordId}/{@code jobId} 入参并在事务内重新加载实体,避免使用 consumer 持有的
 * 游离态(detached)对象触发 {@code LazyInitializationException}。
 *
 * @author xiaozhanke
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentJobExecutionService {

    private final DeploymentJobRepository deploymentJobRepository;
    private final DeploymentRecordRepository deploymentRecordRepository;
    private final FileStorageService fileStorageService;

    /**
     * START / RESTART 成功:作业置 SUCCESS,并刷新部署记录运行态投影为"运行中"。
     */
    @Transactional
    public void markStartSuccess(String jobId, String deploymentRecordId, String processId) {
        DeploymentRecord record = loadRecord(deploymentRecordId);
        applyRunningProjection(record, processId);
        deploymentRecordRepository.save(record);
        completeJob(jobId);
    }

    /**
     * STOP 成功:作业置 SUCCESS,并刷新部署记录运行态投影为"已停止"。
     */
    @Transactional
    public void markStopSuccess(String jobId, String deploymentRecordId) {
        DeploymentRecord record = loadRecord(deploymentRecordId);
        record.setRunning(false)
                .setLastStopTime(LocalDateTime.now())
                .setProcessId(null);
        deploymentRecordRepository.save(record);
        completeJob(jobId);
    }

    /**
     * UPDATE 成功(后端应用):换 fileRecord 指针 + 刷新运行态投影为"运行中"(新进程 PID)。
     *
     * <p>fileRecord 指针的持久化推迟到 SSH 重启**成功之后**(本方法):SSH 不在 DB 事务内,
     * 失败时作业进死信、指针保持旧值不被污染。
     */
    @Transactional
    public void markUpdateBackendSuccess(String jobId, String deploymentRecordId,
                                         String fileRecordId, String processId) {
        DeploymentRecord record = loadRecord(deploymentRecordId);
        record.setFileRecord(fileStorageService.getFileRecordReference(fileRecordId));
        applyRunningProjection(record, processId);
        deploymentRecordRepository.save(record);
        completeJob(jobId);
    }

    /**
     * UPDATE 成功(前端应用):换 fileRecord 指针 + 置部署记录 SUCCESS(前端无进程,不动运行态)。
     */
    @Transactional
    public void markUpdateFrontendSuccess(String jobId, String deploymentRecordId, String fileRecordId) {
        DeploymentRecord record = loadRecord(deploymentRecordId);
        record.setFileRecord(fileStorageService.getFileRecordReference(fileRecordId))
                .setStatus(DeploymentStatusEnum.SUCCESS)
                .setErrorMessage(null);
        deploymentRecordRepository.save(record);
        completeJob(jobId);
    }

    /**
     * 作业进入死信终态:置 DEAD + 错误信息 + 应用层重试次数 + 结束时间(不动部署记录投影)。
     */
    @Transactional
    public void markDead(String jobId, String reason, int retryCount) {
        DeploymentJob job = loadJob(jobId);
        job.setStatus(JobStatusEnum.DEAD)
                .setEndTime(LocalDateTime.now())
                .setRetryCount(retryCount)
                .setErrorMessage(ErrorMessageUtils.truncate(reason));
        deploymentJobRepository.save(job);
    }

    /**
     * 刷新部署记录运行态投影为"运行中"(START 与 UPDATE-后端 共用的字段写入)。
     */
    private void applyRunningProjection(DeploymentRecord record, String processId) {
        record.setStatus(DeploymentStatusEnum.SUCCESS)
                .setRunning(true)
                .setProcessId(processId)
                .setLastStartTime(LocalDateTime.now())
                .setErrorMessage(null);
    }

    private void completeJob(String jobId) {
        DeploymentJob job = loadJob(jobId);
        job.setStatus(JobStatusEnum.SUCCESS)
                .setEndTime(LocalDateTime.now())
                .setErrorMessage(null);
        deploymentJobRepository.save(job);
    }

    private DeploymentRecord loadRecord(String deploymentRecordId) {
        return deploymentRecordRepository.findById(deploymentRecordId)
                .orElseThrow(() -> new IllegalStateException("部署记录不存在: " + deploymentRecordId));
    }

    private DeploymentJob loadJob(String jobId) {
        return deploymentJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("部署作业不存在: " + jobId));
    }
}
