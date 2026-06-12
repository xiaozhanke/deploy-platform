package com.xiaozhanke.deploy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xiaozhanke.deploy.enums.ApplicationTypeEnum;
import com.xiaozhanke.deploy.exception.ResourceNotFoundException;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import com.xiaozhanke.deploy.model.entity.FileRecord;
import com.xiaozhanke.deploy.model.entity.HostRecord;
import com.xiaozhanke.deploy.model.mapper.DeploymentRecordPoVoMapper;
import com.xiaozhanke.deploy.model.request.DeploymentParams;
import com.xiaozhanke.deploy.model.vo.DeploymentRecordVo;
import com.xiaozhanke.deploy.repository.DeploymentRecordRepository;
import com.xiaozhanke.deploy.repository.FileRecordRepository;
import com.xiaozhanke.deploy.repository.HostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证 DeploymentService.createDeployment / updateDeployment 使用 getReferenceById 代理而不是全字段 SELECT。
 *
 * <p>新行为：仅以 existsByIdAndDeletedIsFalse 探活后通过 {@code getReferenceById} 取代理，
 * 避免无谓地把 HostRecord 的连接凭据、FileRecord 的元数据加载进内存。
 * 同时 ID 不存在时仍抛 ResourceNotFoundException，行为不变。
 *
 * @author xiaozhanke
 */
@ExtendWith(MockitoExtension.class)
class DeploymentServiceCreateReferenceTest {

    @Mock
    private DeploymentRecordRepository deploymentRecordRepository;

    @Mock
    private DeploymentRecordPoVoMapper deploymentRecordPoVoMapper;

    @Mock
    private SshService sshService;

    @Mock
    private HostRepository hostRepository;

    @Mock
    private FileRecordRepository fileRecordRepository;

    private HostService hostService;
    private FileStorageService fileStorageService;
    private DeploymentService deploymentService;

    @BeforeEach
    void setUp() {
        hostService = new HostService(hostRepository, sshService, mock(), mock());
        fileStorageService = new FileStorageService(fileRecordRepository, mock(),
                new com.xiaozhanke.deploy.config.FileStorageProperties(java.util.List.of()));
        deploymentService = new DeploymentService(deploymentRecordRepository, deploymentRecordPoVoMapper,
                mock(), mock(), sshService, hostService, fileStorageService, mock());
    }

    @Test
    void createDeploymentUsesReferenceProxiesNotFullSelect() {
        String hostId = "host-1";
        String fileId = "file-1";

        HostRecord hostProxy = new HostRecord();
        hostProxy.setId(hostId);
        FileRecord fileProxy = new FileRecord();
        fileProxy.setId(fileId);

        when(hostRepository.existsByIdAndDeletedIsFalse(hostId)).thenReturn(true);
        when(hostRepository.getReferenceById(hostId)).thenReturn(hostProxy);
        when(fileRecordRepository.existsByIdAndDeletedIsFalse(fileId)).thenReturn(true);
        when(fileRecordRepository.getReferenceById(fileId)).thenReturn(fileProxy);
        when(deploymentRecordRepository.save(any(DeploymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentRecordPoVoMapper.poToVo(any(DeploymentRecord.class)))
                .thenReturn(new DeploymentRecordVo());

        DeploymentParams params = new DeploymentParams();
        params.setHostRecordId(hostId);
        params.setFileRecordId(fileId);
        params.setApplicationType(ApplicationTypeEnum.BACKEND);
        params.setPort(8080);

        deploymentService.createDeployment(params);

        // 必须走代理路径：findByIdAndDeletedIsFalse 不应被调用
        verify(hostRepository, never()).findByIdAndDeletedIsFalse(any());
        verify(fileRecordRepository, never()).findByIdAndDeletedIsFalse(any());
        verify(hostRepository, times(1)).getReferenceById(hostId);
        verify(fileRecordRepository, times(1)).getReferenceById(fileId);
    }

    @Test
    void createDeploymentRejectsMissingHostId() {
        when(hostRepository.existsByIdAndDeletedIsFalse("ghost")).thenReturn(false);

        DeploymentParams params = new DeploymentParams();
        params.setHostRecordId("ghost");
        params.setFileRecordId("file-1");

        assertThatThrownBy(() -> deploymentService.createDeployment(params))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost");

        // 不应再读 FileRecord，且不应触发 save
        verify(fileRecordRepository, never()).existsByIdAndDeletedIsFalse(any());
        verify(deploymentRecordRepository, never()).save(any());
    }

    @Test
    void createDeploymentRejectsMissingFileId() {
        when(hostRepository.existsByIdAndDeletedIsFalse("host-1")).thenReturn(true);
        when(hostRepository.getReferenceById("host-1")).thenReturn(new HostRecord());
        when(fileRecordRepository.existsByIdAndDeletedIsFalse("ghost-file")).thenReturn(false);

        DeploymentParams params = new DeploymentParams();
        params.setHostRecordId("host-1");
        params.setFileRecordId("ghost-file");

        assertThatThrownBy(() -> deploymentService.createDeployment(params))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost-file");
        verify(deploymentRecordRepository, never()).save(any());
    }

    @Test
    void hostServiceGetHostReferenceReturnsProxyWithoutFullSelect() {
        HostRecord proxy = new HostRecord();
        proxy.setId("host-9");
        when(hostRepository.existsByIdAndDeletedIsFalse("host-9")).thenReturn(true);
        when(hostRepository.getReferenceById("host-9")).thenReturn(proxy);

        HostRecord ref = hostService.getHostReference("host-9");

        assertThat(ref).isSameAs(proxy);
        verify(hostRepository, never()).findByIdAndDeletedIsFalse(any());
    }
}
