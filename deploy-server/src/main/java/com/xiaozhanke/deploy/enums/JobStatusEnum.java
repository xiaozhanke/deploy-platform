package com.xiaozhanke.deploy.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 部署作业状态枚举
 *
 * <p>部署作业的状态机。状态转换约束:
 * <ul>
 *   <li>PENDING → IN_PROGRESS:消费者 CAS UPDATE 占据</li>
 *   <li>IN_PROGRESS → SUCCESS / FAILED:SSH 执行结束</li>
 *   <li>FAILED → DEAD:超过应用层重试次数后进入死信</li>
 *   <li>PENDING → CANCELLED:延迟作业的用户撤销</li>
 * </ul>
 *
 * @author xiaozhanke
 */
@Getter
@AllArgsConstructor
public enum JobStatusEnum {
    /**
     * 待消费(已写入,等待 consumer 占据)
     */
    PENDING("待执行"),

    /**
     * 消费中(CAS 占据成功,正在执行 SSH)
     */
    IN_PROGRESS("执行中"),

    /**
     * 执行成功
     */
    SUCCESS("成功"),

    /**
     * 执行失败(等待应用层重试或转入 DEAD)
     */
    FAILED("失败"),

    /**
     * 死信(超过重试上限或人工标记)
     */
    DEAD("死信"),

    /**
     * 已取消(延迟作业被用户撤销)
     */
    CANCELLED("已取消");

    private final String description;
}
