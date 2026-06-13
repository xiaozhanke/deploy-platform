package com.xiaozhanke.deploy.monitor;

/**
 * 一次远程命令执行的结果（监控链路专用）。
 *
 * @param exitStatus 远程命令退出码（JSch 在通道关闭后给出；取不到时为 -1）
 * @param stdout     标准输出（UTF-8）
 * @author xiaozhanke
 */
public record SshCommandResult(int exitStatus, String stdout) {
}
