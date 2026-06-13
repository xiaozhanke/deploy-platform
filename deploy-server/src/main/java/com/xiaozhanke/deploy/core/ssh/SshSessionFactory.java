package com.xiaozhanke.deploy.core.ssh;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.xiaozhanke.deploy.constant.SshConstants;
import com.xiaozhanke.deploy.enums.SshAuthTypeEnum;
import com.xiaozhanke.deploy.exception.BusinessException;
import com.xiaozhanke.deploy.model.dto.HostRecordDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * JSch 会话工厂——把「{@link HostRecordDto} → 配置好认证与加密参数的 {@link Session}」这段会话构建逻辑
 * 收敛为唯一来源。
 *
 * <p>本工厂只负责<strong>创建并配置</strong>会话，<strong>不</strong>调用 {@code session.connect()}、
 * 也不维护任何会话生命周期——连接、复用、断开由调用方按各自的连接模型决定：
 * <ul>
 *   <li>{@link com.xiaozhanke.deploy.service.SshService}——交互式终端 / SFTP 的持久会话池（带全局锁）；</li>
 *   <li>免 Agent 资源监控——采样器独占的 per-host 长连接池 + 在线检测短连接，
 *       与 SshService 的会话池<strong>不共享</strong>，仅共享本工厂的会话构建逻辑。</li>
 * </ul>
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
public class SshSessionFactory {

    /**
     * 创建并配置 JSch Session 对象（未连接）。
     *
     * @param host 连接详情
     * @return 配置好认证、加密算法与保活参数的 Session 对象（调用方负责 connect）
     * @throws JSchException 如果会话创建或配置失败
     */
    public Session createSession(HostRecordDto host) throws JSchException {
        log.debug("创建 JSch 实例并配置认证, 用户: {}", host.getUsername());
        JSch jsch = new JSch();
        // 设置认证方式
        setupAuth(jsch, host);

        log.debug("获取 JSch 会话实例, 服务器: {}@{} -p {}", host.getUsername(), host.getAddress(), host.getPort());
        Session session = jsch.getSession(host.getUsername(), host.getAddress(), host.getPort());

        // 如果是密码认证, 设置密码（用 byte[] 重载，避免 JSch 内部把 String 留在 String pool）
        if (host.getAuthType() == SshAuthTypeEnum.PASSWORD && host.getPassword() != null) {
            session.setPassword(host.getPassword().getBytes(StandardCharsets.UTF_8));
            log.debug("已设置会话密码 (密码本身不记录日志)");
        }

        // 应用其他会话配置
        applySessionConfig(session, host);

        session.setServerAliveCountMax(SshConstants.DEFAULT_SERVER_ALIVE_COUNT_MAX);
        session.setServerAliveInterval(SshConstants.DEFAULT_SERVER_ALIVE_INTERVAL);
        return session;
    }

    /**
     * 设置认证方式
     *
     * @param jsch JSch 对象
     * @param host 主机连接信息
     * @throws JSchException 认证设置失败时抛出
     */
    private void setupAuth(JSch jsch, HostRecordDto host) throws JSchException {
        SshAuthTypeEnum authType = host.getAuthType();
        log.debug("配置认证类型: {}", authType);

        if (authType == SshAuthTypeEnum.KEY || authType == SshAuthTypeEnum.KEY_WITH_PASS) {
            String privateKeyPath = host.getPrivateKeyPath();
            if (!StringUtils.hasText(privateKeyPath)) {
                throw new BusinessException("私钥认证需要提供私钥文件路径");
            }
            File privateKeyFile = new File(privateKeyPath);
            if (!privateKeyFile.exists() || !privateKeyFile.isFile()) {
                throw new BusinessException("指定的私钥文件不存在或不是一个有效文件: " + privateKeyPath);
            }

            String passphrase = (authType == SshAuthTypeEnum.KEY_WITH_PASS) ? host.getPrivateKeyPassword() : null;
            try {
                // 用 byte[] 重载，避免 passphrase 以 String 形式被 JSch 内部缓存
                byte[] passphraseBytes = passphrase == null ? null : passphrase.getBytes(StandardCharsets.UTF_8);
                jsch.addIdentity(privateKeyPath, null, passphraseBytes);
                log.info("已添加私钥身份: {}", privateKeyPath);
            } catch (JSchException e) {
                log.error("添加私钥身份失败 '{}': {}", privateKeyPath, e.getMessage());
                if (e.getMessage().toLowerCase().contains("passphrase")) {
                    throw new JSchException("私钥密码错误或需要密码但未提供: " + privateKeyPath, e);
                }
                throw e;
            }
        } else if (authType == null || authType == SshAuthTypeEnum.PASSWORD) {
            // 密码认证由 session.setPassword() 处理
            log.debug("使用密码认证方式");
        } else {
            throw new BusinessException("不支持的认证类型: " + authType);
        }
    }

    /**
     * 应用 Session 配置
     *
     * @param session Session 对象
     * @param host    主机连接信息
     */
    private void applySessionConfig(Session session, HostRecordDto host) throws JSchException {
        log.debug("开始应用会话配置");
        // 设置加密算法等配置
        setIfNotNull(session, "kex", host.getKexAlgorithms());
        setIfNotNull(session, "cipher.s2c", host.getCipherAlgorithms());
        setIfNotNull(session, "cipher.c2s", host.getCipherAlgorithms());
        setIfNotNull(session, "mac.s2c", host.getMacAlgorithms());
        setIfNotNull(session, "mac.c2s", host.getMacAlgorithms());
        setIfNotNull(session, "server_host_key", host.getServerHostKeyAlgorithms());

        // 设置连接超时
        if (host.getConnectionTimeout() != null) {
            session.setTimeout(host.getConnectionTimeout());
        }

        // 设置布尔型配置项
        setBooleanConfig(session, "compression", host.getCompressionEnabled(), "zlib", "none");
        setBooleanConfig(session, "StrictHostKeyChecking", host.getStrictHostKeyChecking(), "yes", "no");
        setBooleanConfig(session, "X11Forwarding", host.getX11ForwardingEnabled(), "yes", "no");
        setBooleanConfig(session, "AllowTcpForwarding", host.getPortForwardingEnabled(), "yes", "no");
        log.debug("会话配置应用完毕");
    }

    /**
     * 设置 Session 配置项(非空时设置)
     *
     * @param session Session 对象
     * @param key     配置键
     * @param value   配置值
     */
    private void setIfNotNull(Session session, String key, String value) {
        if (StringUtils.hasText(value)) {
            session.setConfig(key, value);
            log.debug("设置 Session 配置项: {} = {}", key, value);
        }
    }

    /**
     * 设置布尔型 Session 配置项
     *
     * @param session    Session 对象
     * @param key        配置键
     * @param value      布尔值
     * @param trueValue  为 true 时的配置值
     * @param falseValue 为 false 时的配置值
     */
    private void setBooleanConfig(Session session, String key, Boolean value, String trueValue, String falseValue) {
        if (value != null) {
            session.setConfig(key, value ? trueValue : falseValue);
            log.debug("设置布尔型 Session 配置项: {} = {}", key, value ? trueValue : falseValue);
        }
    }
}
