package com.xiaozhanke.deploy.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审计操作类型枚举(对应 MQ 方案稿场景 4)。
 *
 * <p>由 {@code @Auditable} 注解在切点上声明,经 Kafka 异步采集后落 {@code audit_log} 表。
 * 安全审计要求"全量记录尝试",因此每种类型都可能携带 {@link AuditOutcomeEnum#SUCCESS} 或
 * {@link AuditOutcomeEnum#FAILURE} 两种结果(详见 ADR-0005 的双切面语义)。
 *
 * @author xiaozhanke
 */
@Getter
@AllArgsConstructor
public enum AuditOperationTypeEnum {
    /**
     * 远程 SSH 命令执行(浏览器终端 Exec 通道)
     */
    SSH_EXEC("SSH 命令执行"),

    /**
     * 文件上传(SFTP 或本地仓库)
     */
    FILE_UPLOAD("文件上传"),

    /**
     * 文件下载
     */
    FILE_DOWNLOAD("文件下载"),

    /**
     * 文件删除
     */
    FILE_DELETE("文件删除"),

    /**
     * 用户登录
     */
    LOGIN("登录"),

    /**
     * 用户登出
     */
    LOGOUT("登出"),

    /**
     * 创建部署记录
     */
    DEPLOYMENT_CREATE("创建部署记录"),

    /**
     * 更新部署记录
     */
    DEPLOYMENT_UPDATE("更新部署记录"),

    /**
     * 删除部署记录
     */
    DEPLOYMENT_DELETE("删除部署记录"),

    /**
     * 创建部署作业
     */
    JOB_CREATE("创建部署作业"),

    /**
     * 取消部署作业(PENDING → CANCELLED)
     */
    JOB_CANCEL("取消部署作业"),

    /**
     * 创建服务器
     */
    SERVER_CREATE("创建服务器"),

    /**
     * 更新服务器
     */
    SERVER_UPDATE("更新服务器"),

    /**
     * 删除服务器
     */
    SERVER_DELETE("删除服务器"),

    /**
     * 创建用户
     */
    USER_CREATE("创建用户"),

    /**
     * 更新用户
     */
    USER_UPDATE("更新用户"),

    /**
     * 删除用户
     */
    USER_DELETE("删除用户"),

    /**
     * 创建角色
     */
    ROLE_CREATE("创建角色"),

    /**
     * 更新角色
     */
    ROLE_UPDATE("更新角色"),

    /**
     * 删除角色
     */
    ROLE_DELETE("删除角色");

    private final String description;
}
