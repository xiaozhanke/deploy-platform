package com.xiaozhanke.deploy.monitor;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.xiaozhanke.deploy.constant.SshConstants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 监控链路的底层命令执行：在<strong>已连接</strong>的 {@link Session} 上开临时 {@link ChannelExec}
 * 跑一条命令、读尽标准输出、读取退出码后关闭通道（保留 Session）。
 *
 * <p>由两类调用方共用，统一命令执行细节：
 * <ul>
 *   <li>{@link MonitorSshConnectionPool}——采样器的 per-host 长连接；</li>
 *   <li>{@code LivenessProbeService}——在线检测的短连接。</li>
 * </ul>
 * 标准错误单独丢弃，避免与标准输出混读。会话级超时（{@code session.setTimeout}）由调用方按监控参数设置，
 * 保证读不到数据时不会永久阻塞。
 *
 * @author xiaozhanke
 */
public final class MonitorChannelExec {

    private MonitorChannelExec() {
    }

    /**
     * 在已连接会话上执行一条命令。
     *
     * @param session              已连接的 JSch 会话
     * @param command              远程命令（应为只读的 {@code /proc} 解析或 {@code echo} 等无副作用命令）
     * @param channelTimeoutMillis 通道连接超时（毫秒）
     * @return 退出码 + 标准输出
     * @throws JSchException 通道打开/连接失败
     * @throws IOException   读取标准输出失败
     */
    public static SshCommandResult run(Session session, String command, int channelTimeoutMillis)
            throws JSchException, IOException {
        ChannelExec channel = (ChannelExec) session.openChannel(SshConstants.ChannelType.EXEC);
        try {
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(new ByteArrayOutputStream());
            try (InputStream stdout = channel.getInputStream()) {
                channel.connect(channelTimeoutMillis);
                String output = readFully(stdout);
                return new SshCommandResult(channel.getExitStatus(), output);
            }
        } finally {
            channel.disconnect();
        }
    }

    private static String readFully(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[SshConstants.BUFFER_SIZE];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
