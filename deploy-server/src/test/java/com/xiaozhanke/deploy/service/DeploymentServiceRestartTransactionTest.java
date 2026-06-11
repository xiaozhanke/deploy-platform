package com.xiaozhanke.deploy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

import com.xiaozhanke.deploy.enums.ApplicationTypeEnum;
import com.xiaozhanke.deploy.enums.DeploymentStatusEnum;
import com.xiaozhanke.deploy.enums.SshAuthTypeEnum;
import com.xiaozhanke.deploy.exception.BusinessException;
import com.xiaozhanke.deploy.model.dto.HostRecordDto;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import com.xiaozhanke.deploy.model.entity.FileRecord;
import com.xiaozhanke.deploy.model.entity.HostRecord;
import com.xiaozhanke.deploy.repository.DeploymentRecordRepository;
import com.xiaozhanke.deploy.repository.FileRecordRepository;
import com.xiaozhanke.deploy.repository.HostRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 验证 {@link DeploymentService#restartApplication(String)} 是否能在 start 阶段失败时回滚 stop 阶段
 * 已写入的状态变更，避免出现 running=false / processId=null 这种"已停止但未启动"的不一致快照。
 *
 * <p>之前 restartApplication 形如 {@code stopApplication(id); return startApplication(id);}，
 * 依赖 Spring AOP self-call 的隐式行为绑定单事务，重构成 doStop/doStart 私有方法 + 单 @Transactional
 * 之后必须有用例锁死这一行为，防止后续有人误把私有方法重新拆成独立事务。
 *
 * <p>连独立 MySQL 测试库 restart_tx_test（createDatabaseIfNotExist 自动建、ddl-auto=create-drop 用完即弃）
 * 避免污染开发态 deploy_platform；JWT keystore 路径指到 target/，由 mvn clean 兜底清理。
 *
 * @author xiaozhanke
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/restart_tx_test?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
        "spring.datasource.username=${MYSQL_USER:root}",
        "spring.datasource.password=${MYSQL_PASSWORD:123456}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=never",
        "app.security.jwt.key-file=target/test-jwt-restart-key.json",
        "app.security.jwt.auto-create-on-missing=true"
})
class DeploymentServiceRestartTransactionTest {

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private DeploymentRecordRepository deploymentRecordRepository;

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private FileRecordRepository fileRecordRepository;

    @MockitoBean
    private SshService sshService;

    @MockitoBean
    private HostService hostService;

    private DeploymentRecord seed;

    @BeforeEach
    void setUp() {
        deploymentRecordRepository.deleteAll();
        fileRecordRepository.deleteAll();
        hostRepository.deleteAll();

        HostRecord host = new HostRecord();
        host.setName("test-host");
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
                .setLastStartTime(LocalDateTime.now())
                .setProcessId("999")
                .setRunning(true);
        this.seed = deploymentRecordRepository.save(deployment);

        when(hostService.getHostDto(any())).thenReturn(new HostRecordDto());
    }

    @Test
    void restartRollsBackStopWhenStartFails() {
        when(sshService.executeCommand(any(), startsWith("kill"))).thenReturn("");
        when(sshService.executeCommand(any(), contains("nohup")))
                .thenThrow(new RuntimeException("启动失败"));

        assertThatThrownBy(() -> deploymentService.restartApplication(seed.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("启动应用失败");

        DeploymentRecord reloaded = deploymentRecordRepository.findById(seed.getId()).orElseThrow();
        assertThat(reloaded.getRunning()).isTrue();
        assertThat(reloaded.getProcessId()).isEqualTo("999");
    }

    @Test
    void restartPersistsNewProcessIdOnSuccess() {
        when(sshService.executeCommand(any(), startsWith("kill"))).thenReturn("");
        when(sshService.executeCommand(any(), contains("nohup"))).thenReturn("12345\n");

        deploymentService.restartApplication(seed.getId());

        DeploymentRecord reloaded = deploymentRecordRepository.findById(seed.getId()).orElseThrow();
        assertThat(reloaded.getRunning()).isTrue();
        assertThat(reloaded.getProcessId()).isEqualTo("12345");
    }
}
