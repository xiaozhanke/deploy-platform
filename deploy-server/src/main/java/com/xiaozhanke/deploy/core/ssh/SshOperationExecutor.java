package com.xiaozhanke.deploy.core.ssh;

import com.jcraft.jsch.JSchException;
import com.xiaozhanke.deploy.exception.BusinessException;
import com.xiaozhanke.deploy.exception.JobFailureException;
import com.xiaozhanke.deploy.exception.SshTransientException;
import com.xiaozhanke.deploy.model.dto.HostRecordDto;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import com.xiaozhanke.deploy.service.HostService;
import com.xiaozhanke.deploy.service.SshService;
import com.xiaozhanke.deploy.util.ShellArgEscaper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * SSH 部署操作执行器
 *
 * <p>把"在远程主机执行 start/stop 命令"这件事从 {@code DeploymentService} 里抽出来,供
 * {@code DeploymentService}(旧同步接口)与 {@code DeploymentConsumer}(MQ 消费路径)共用。
 *
 * <p>本类**只**负责命令构造与 SSH 调用,**不**负责数据库状态更新——状态机推进由调用方决定
 * (老 service 在同一事务里 save,consumer 在作业完成后由 {@code DeploymentJobExecutionService} 写)。
 *
 * <p>对 MQ 消费路径,本类把 {@link SshService} 抛出的 {@link BusinessException} 按 cause **分级**
 * (ADR-0003):连接/通道/IO 类(瞬时,可短重试)→ {@link SshTransientException};命令退出码非 0 等
 * (业务失败,重试无益)→ {@link JobFailureException}。旧同步路径仍按 {@code RuntimeException} 捕获,
 * 不受影响。
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SshOperationExecutor {

    private final SshService sshService;
    private final HostService hostService;

    /**
     * 在远程主机启动应用,返回新进程的 PID(字符串)。
     */
    public String executeStart(DeploymentRecord deployment) {
        HostRecordDto host = hostService.getHostDto(deployment.getHostRecord().getId());

        // 所有取自用户输入的字段都套上单引号字面值,避免 ; && ` $() 这类元字符触发命令注入
        String command = String.format(
                "cd %s; " +
                        "nohup java -jar %s --server.port=%d %s --spring.profiles.active=%s > nohup.out 2>&1 & " +
                        "PID=$!; " +
                        "echo $PID; " +
                        "disown $PID; " +
                        "exit 0",
                ShellArgEscaper.singleQuote(deployment.getDeploymentPath()),
                ShellArgEscaper.singleQuote(deployment.getFileRecord().getFileName()),
                deployment.getPort(),
                ShellArgEscaper.singleQuote(deployment.getProgramArgs()),
                ShellArgEscaper.singleQuote(deployment.getActiveProfiles())
        );

        String result = runJobCommand(host, command);
        return result.trim();
    }

    /**
     * 在远程主机停止应用进程(kill -15)。
     */
    public void executeStop(DeploymentRecord deployment) {
        HostRecordDto host = hostService.getHostDto(deployment.getHostRecord().getId());

        // processId 必须是纯数字,否则 shell 会按 jobspec 解析或被注入额外语义
        String command = String.format("kill -15 %s",
                ShellArgEscaper.requireNumericProcessId(deployment.getProcessId()));
        runJobCommand(host, command);
    }

    /**
     * 执行 SSH 命令并把失败按 ADR-0003 分级:连接/通道/IO 类基础设施异常 → {@link SshTransientException}
     * (可短重试);其余(命令退出码非 0 等业务失败)→ {@link JobFailureException}(重试无益)。
     */
    private String runJobCommand(HostRecordDto host, String command) {
        try {
            return sshService.executeCommand(host, command);
        } catch (BusinessException e) {
            Throwable cause = e.getCause();
            if (cause instanceof JSchException || cause instanceof IOException) {
                throw new SshTransientException(e.getMessage(), e);
            }
            throw new JobFailureException(e.getMessage(), e);
        }
    }
}
