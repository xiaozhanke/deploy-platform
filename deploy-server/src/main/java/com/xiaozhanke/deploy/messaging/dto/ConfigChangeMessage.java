package com.xiaozhanke.deploy.messaging.dto;

import java.time.LocalDateTime;

/**
 * 配置变更广播消息体(对应 MQ 方案稿场景 6)。
 *
 * <p>管理员修改全局配置后,producer 把变更广播给所有实例;各实例的 consumer 据此
 * 刷新本地缓存或重新加载配置。与部署作业走 CLUSTERING 相对,配置变更 Topic 使用
 * BROADCASTING 模式——每条消息必须送达每个实例,而非实例间分摊。
 *
 * @author xiaozhanke
 */
public record ConfigChangeMessage(
        /** 配置键(如 "ssh.timeout-seconds") */
        String configKey,
        /** 新值(已序列化为字符串) */
        String newValue,
        /** 变更类型 */
        ChangeType changeType,
        /** 操作人 */
        String operator,
        /** 变更时间 */
        LocalDateTime changeTime
) {
    public enum ChangeType {
        /** 新增配置项 */
        CREATED,
        /** 更新已有配置项 */
        UPDATED,
        /** 删除配置项 */
        DELETED
    }
}
