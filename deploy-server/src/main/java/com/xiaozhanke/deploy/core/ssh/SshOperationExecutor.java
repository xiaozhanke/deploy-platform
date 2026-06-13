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
 * <p>封装"在远程主机执行 start/stop/unzip 命令"的命令构造与 SSH 调用,由 MQ 消费路径
 * ({@code JobExecutionDelegate})调用。
 *
 * <p>本类**只**负责命令构造与 SSH 调用,**不**负责数据库状态更新——作业完成后的状态机推进
 * 由 {@code DeploymentJobExecutionService} 写。
 *
 * <p>把 {@link SshService} 抛出的 {@link BusinessException} 按 cause **分级**:连接/通道/IO 类
 * (瞬时,可短重试)→ {@link SshTransientException};其余(命令退出码非 0 等业务失败,重试无益)
 * → {@link JobFailureException}。
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
     * 在远程主机解压前端应用包到部署目录(UPDATE 作业,前端应用)。
     *
     * <p>包已由前端在提交作业前经 SFTP 上传到 {@code deploymentPath},本方法只负责 {@code unzip -o}
     * 覆盖解压。文件名取自 {@code deployment.getFileRecord()}——调用方在调用前已把部署记录的
     * fileRecord 换成目标新包(内存态)。
     */
    public void executeUnzip(DeploymentRecord deployment) {
        HostRecordDto host = hostService.getHostDto(deployment.getHostRecord().getId());

        // deploymentPath 与 fileName 取自用户输入,必须套单引号字面值,避免命令注入
        String command = String.format(
                "cd %s && unzip -o %s",
                ShellArgEscaper.singleQuote(deployment.getDeploymentPath()),
                ShellArgEscaper.singleQuote(deployment.getFileRecord().getFileName())
        );
        runJobCommand(host, command);
    }

    /**
     * 执行 SSH 命令并把失败分级:连接/通道/IO 类基础设施异常 → {@link SshTransientException}
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
