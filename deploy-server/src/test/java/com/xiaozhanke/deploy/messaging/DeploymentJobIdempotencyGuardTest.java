package com.xiaozhanke.deploy.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xiaozhanke.deploy.enums.ApplicationTypeEnum;
import com.xiaozhanke.deploy.enums.DeploymentStatusEnum;
import com.xiaozhanke.deploy.enums.JobStatusEnum;
import com.xiaozhanke.deploy.enums.JobTypeEnum;
import com.xiaozhanke.deploy.enums.SshAuthTypeEnum;
import com.xiaozhanke.deploy.messaging.idempotent.AcquireResult;
import com.xiaozhanke.deploy.messaging.idempotent.JobAcquisitionService;
import com.xiaozhanke.deploy.messaging.producer.DeploymentMQProducer;
import com.xiaozhanke.deploy.messaging.transaction.DeploymentTransactionListener;
import com.xiaozhanke.deploy.model.entity.DeploymentJob;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import com.xiaozhanke.deploy.model.entity.FileRecord;
import com.xiaozhanke.deploy.model.entity.HostRecord;
import com.xiaozhanke.deploy.model.request.CreateJobRequest;
import com.xiaozhanke.deploy.model.vo.DeploymentJobVo;
import com.xiaozhanke.deploy.repository.DeploymentJobRepository;
import com.xiaozhanke.deploy.repository.DeploymentRecordRepository;
import com.xiaozhanke.deploy.repository.FileRecordRepository;
import com.xiaozhanke.deploy.repository.HostRepository;
import com.xiaozhanke.deploy.service.DeploymentJobService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 防重三道关的端到端单测(对应 ADR-0002 与 docs/mq-phase1-verify.md 第 8 节)。
 *
 * <p>把"同一次部署作业被重复触发/重投"时三层防御逐一锁死,任何一层被后续改动削弱都会让本类变红:
 * <ol>
 *   <li><b>第一关——本地事务唯一索引</b>:{@link DeploymentTransactionListener#executeLocalTransaction}
 *       对重复三元组 {@code (deployment_record_id, job_type, client_request_id)} 应返回 {@code ROLLBACK},
 *       丢弃半消息(并发兜底:两请求同时穿过第二关 SELECT 时由 DB 唯一索引收口)
 *   <li><b>第二关——HTTP 入口三元组查重</b>:{@link DeploymentJobService#createJob} 命中已有作业时
 *       直接返回旧作业,既不发事务消息也不新增行(乐观快路径,挡掉绝大多数重复点击)
 *   <li><b>第三关——消费端 CAS 占据</b>:{@link JobAcquisitionService#acquire} 用业务键 jobId 做
 *       {@code UPDATE ... WHERE status=PENDING},首占成功、重投 affected=0(消费幂等)
 * </ol>
 *
 * <p>第一关靠真实 MySQL 唯一索引、第三关靠真实 SQL 的 affected rows 语义,均无法用 mock 替代,
 * 故沿用 {@code DeploymentServiceRestartTransactionTest} 的做法连专用测试库
 * {@code idempotency_test}({@code createDatabaseIfNotExist} + {@code ddl-auto=create-drop},
 * 不污染开发态 {@code deploy_tool});JWT keystore 指向 target/ 由 mvn clean 兜底清理。
 * 仅第二关需要避开真实 broker,用 {@link MockitoBean} 桩掉 {@link DeploymentMQProducer}。
 *
 * @author xiaozhanke
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/idempotency_test?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
        "spring.datasource.username=${MYSQL_USER:root}",
        "spring.datasource.password=${MYSQL_PASSWORD:123456}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=never",
        "app.security.jwt.key-file=target/test-jwt-idempotency-key.json",
        "app.security.jwt.auto-create-on-missing=true"
})
class DeploymentJobIdempotencyGuardTest {

    @Autowired
    private DeploymentJobService deploymentJobService;

    @Autowired
    private DeploymentTransactionListener transactionListener;

    @Autowired
    private JobAcquisitionService jobAcquisitionService;

    @Autowired
    private DeploymentJobRepository deploymentJobRepository;

    @Autowired
    private DeploymentRecordRepository deploymentRecordRepository;

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private FileRecordRepository fileRecordRepository;

    @MockitoBean
    private DeploymentMQProducer deploymentMQProducer;

    private DeploymentRecord seedRecord;

    @BeforeEach
    void setUp() {
        // 删除顺序:job → record → file/host,先清掉外键引用方
        deploymentJobRepository.deleteAll();
        deploymentRecordRepository.deleteAll();
        fileRecordRepository.deleteAll();
        hostRepository.deleteAll();

        HostRecord host = new HostRecord();
        host.setName("idempotency-host");
        host.setAddress("127.0.0.1");
        host.setPort(22);
        host.setUsername("test");
        host.setHomeDir("/tmp");
        host.setAuthType(SshAuthTypeEnum.PASSWORD);
        host.setPassword("dummy");
        host = hostRepository.save(host);

        FileRecord file = new FileRecord();
        file.setFileName("app.jar");
        file.setRelativePath("/dummy");
        file = fileRecordRepository.save(file);

        DeploymentRecord deployment = new DeploymentRecord();
        deployment.setHostRecord(host)
                .setFileRecord(file)
                .setApplicationType(ApplicationTypeEnum.BACKEND)
                .setDeploymentPath("/opt/app")
                .setPort(8080)
                .setProgramArgs("")
                .setActiveProfiles("dev")
                .setStatus(DeploymentStatusEnum.SUCCESS)
                .setDeployTime(LocalDateTime.now())
                .setRunning(false);
        this.seedRecord = deploymentRecordRepository.save(deployment);
    }

    // ---------- 第一关:本地事务唯一索引 ----------

    /**
     * 地基:DB 唯一索引 {@code uk_deployment_job_record_type_request} 物理拦截重复三元组,
     * 不依赖任何应用层逻辑——这是第一关与第二关共同的兜底依据。
     */
    @Test
    void uniqueIndexRejectsDuplicateTriplet() {
        String clientRequestId = UUID.randomUUID().toString();
        deploymentJobRepository.saveAndFlush(newJob(clientRequestId, JobStatusEnum.PENDING));

        // 不同 jobId、相同三元组 → 撞唯一索引
        DeploymentJob duplicate = newJob(clientRequestId, JobStatusEnum.PENDING);
        assertThatThrownBy(() -> deploymentJobRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * 第一关:首条三元组本地事务 COMMIT 落库;同三元组的第二条应被监听器判定为冲突并返回
     * ROLLBACK(半消息丢弃),且第二条不落库、表内仍只有一行。
     */
    @Test
    void localTransactionRollsBackOnDuplicateTriplet() {
        String clientRequestId = UUID.randomUUID().toString();

        DeploymentJob first = newJob(clientRequestId, JobStatusEnum.PENDING);
        RocketMQLocalTransactionState firstState =
                transactionListener.executeLocalTransaction(ANY_HALF_MESSAGE, first);
        assertThat(firstState).isEqualTo(RocketMQLocalTransactionState.COMMIT);
        assertThat(deploymentJobRepository.findById(first.getId())).isPresent();

        DeploymentJob duplicate = newJob(clientRequestId, JobStatusEnum.PENDING);
        RocketMQLocalTransactionState duplicateState =
                transactionListener.executeLocalTransaction(ANY_HALF_MESSAGE, duplicate);

        assertThat(duplicateState).isEqualTo(RocketMQLocalTransactionState.ROLLBACK);
        assertThat(deploymentJobRepository.findById(duplicate.getId())).isEmpty();
        assertThat(deploymentJobRepository.count()).isEqualTo(1);
    }

    // ---------- 第二关:HTTP 入口三元组查重 ----------

    /**
     * 第二关:带同一三元组再次调 createJob,命中已有作业 → 返回旧 jobId,不发事务消息、不新增行。
     */
    @Test
    void httpEntryReturnsExistingJobWithoutSending() {
        String clientRequestId = UUID.randomUUID().toString();
        DeploymentJob existing =
                deploymentJobRepository.saveAndFlush(newJob(clientRequestId, JobStatusEnum.SUCCESS));

        CreateJobRequest request = new CreateJobRequest();
        request.setJobType(JobTypeEnum.START);
        request.setClientRequestId(clientRequestId);

        DeploymentJobVo result = deploymentJobService.createJob(seedRecord.getId(), request);

        assertThat(result.getId()).isEqualTo(existing.getId());
        verify(deploymentMQProducer, never()).sendDeploymentJob(any());
        assertThat(deploymentJobRepository.count()).isEqualTo(1);
    }

    /**
     * 第二关反向用例:三元组未命中 → 调 producer 发事务消息(真实流程里本地事务监听器在半消息提交
     * 回调内 INSERT,本测试无 broker,用桩模拟这步落库并回 SEND_OK),最终作业以 PENDING 入库。
     */
    @Test
    void httpEntryCreatesAndSendsWhenTripletIsNew() {
        when(deploymentMQProducer.sendDeploymentJob(any())).thenAnswer(invocation -> {
            DeploymentJob pending = invocation.getArgument(0);
            deploymentJobRepository.saveAndFlush(pending);
            SendResult sendResult = new SendResult();
            sendResult.setSendStatus(SendStatus.SEND_OK);
            sendResult.setMsgId("TEST-MSG-ID");
            return sendResult;
        });

        String clientRequestId = UUID.randomUUID().toString();
        CreateJobRequest request = new CreateJobRequest();
        request.setJobType(JobTypeEnum.START);
        request.setClientRequestId(clientRequestId);

        DeploymentJobVo result = deploymentJobService.createJob(seedRecord.getId(), request);

        verify(deploymentMQProducer, times(1)).sendDeploymentJob(any());
        assertThat(result.getClientRequestId()).isEqualTo(clientRequestId);
        assertThat(result.getStatus()).isEqualTo(JobStatusEnum.PENDING);
        assertThat(deploymentJobRepository.count()).isEqualTo(1);
    }

    // ---------- 第三关:消费端 CAS 占据 ----------

    /**
     * 第三关:首次 acquire 把 PENDING 经 CAS 改 IN_PROGRESS 并填 startTime,返回 {@code ACQUIRED};
     * 模拟重投的第二次 acquire 因作业已非 PENDING,返回 {@code ALREADY_HANDLED}(消费幂等)。
     */
    @Test
    void consumerCasAcquiresOnceAndBlocksReplay() {
        DeploymentJob pending = deploymentJobRepository.saveAndFlush(
                newJob(UUID.randomUUID().toString(), JobStatusEnum.PENDING));

        assertThat(jobAcquisitionService.acquire(pending.getId(), seedRecord.getId()))
                .isEqualTo(AcquireResult.ACQUIRED);
        assertThat(jobAcquisitionService.acquire(pending.getId(), seedRecord.getId()))
                .isEqualTo(AcquireResult.ALREADY_HANDLED);

        DeploymentJob reloaded = deploymentJobRepository.findById(pending.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(JobStatusEnum.IN_PROGRESS);
        assertThat(reloaded.getStartTime()).isNotNull();
    }

    /**
     * 场景 2(ADR-0006):同一份部署记录的第二个作业在前一个仍 IN_PROGRESS 时占据失败,返回
     * {@code RECORD_BUSY}(让 ORDERLY 稍后重投),且自身仍为 PENDING——保证记录级串行、不并发。
     */
    @Test
    void consumerCasSerializesJobsOfSameRecord() {
        DeploymentJob first = deploymentJobRepository.saveAndFlush(
                newJob(UUID.randomUUID().toString(), JobStatusEnum.PENDING));
        DeploymentJob second = deploymentJobRepository.saveAndFlush(
                newJob(UUID.randomUUID().toString(), JobStatusEnum.PENDING));

        // 第一个作业占据成功 → 该记录进入 IN_PROGRESS
        assertThat(jobAcquisitionService.acquire(first.getId(), seedRecord.getId()))
                .isEqualTo(AcquireResult.ACQUIRED);
        // 同一记录的第二个作业:自身仍 PENDING,但记录已有在途作业 → RECORD_BUSY
        assertThat(jobAcquisitionService.acquire(second.getId(), seedRecord.getId()))
                .isEqualTo(AcquireResult.RECORD_BUSY);
        // 第二个作业未被推进,仍是 PENDING(稍后重投时再占)
        assertThat(deploymentJobRepository.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(JobStatusEnum.PENDING);
    }

    // ---------- 辅助 ----------

    private DeploymentJob newJob(String clientRequestId, JobStatusEnum status) {
        return new DeploymentJob()
                .setId(UUID.randomUUID().toString())
                .setDeploymentRecord(seedRecord)
                .setJobType(JobTypeEnum.START)
                .setStatus(status)
                .setClientRequestId(clientRequestId)
                .setRetryCount(0);
    }

    // executeLocalTransaction 只用 arg(待入库的 DeploymentJob),从不读 msg——jobId header 仅
    // checkLocalTransaction 回查才用,本类未涉及,故所有调用共用一个占位半消息即可。
    private static final Message<String> ANY_HALF_MESSAGE =
            MessageBuilder.withPayload("ignored").build();
}
