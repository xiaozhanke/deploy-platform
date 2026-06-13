package com.xiaozhanke.deploy.model.dto;

/**
 * 「运行中部署实例」的轻量只读投影，供在线检测按主机分组合并探测 PID 使用。
 *
 * <p>用 JPQL 构造器表达式直接投影出 {@code (id, hostRecord.id, processId)} 三列，<strong>不</strong>加载
 * {@code DeploymentRecord} 整实体、也不触碰 {@code @ManyToOne} 懒加载关联——避免在无事务的定时探测线程里
 * 触发 {@code LazyInitializationException}，同时规避逐实例取 host 的 N+1。
 *
 * @param deploymentRecordId 部署记录 Id（实例存活缓存的 key）
 * @param hostRecordId       所在主机 Id（用于按主机分组、复用同一条短连接探测）
 * @param processId          进程号（应为纯数字；非数字者由调用方剔除，不纳入 {@code ps -p} 命令）
 * @author xiaozhanke
 */
public record RunningInstanceView(String deploymentRecordId, String hostRecordId, String processId) {
}
