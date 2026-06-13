package com.xiaozhanke.deploy.messaging;

import com.xiaozhanke.deploy.repository.DeploymentJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 全平台部署动态（activity）推送器。
 *
 * <p>作业每次状态变更（PENDING / IN_PROGRESS / 终态）时，把该作业的最新快照投影为
 * {@link com.xiaozhanke.deploy.model.vo.ActivityVo} 推送到全局广播频道 {@value #ACTIVITIES_DESTINATION}，
 * 驱动控制台「最新发版动态」时间轴增量更新。该频道为<strong>有意全平台广播</strong>、无 per-record ACL，
 * 任意登录用户经 spring-security-messaging 鉴权后均可订阅。
 *
 * <p>用 JPQL JOIN 投影一次查出 activity 所需的全部字段（作业 + 部署记录 + 主机），避免在 MQ 消费线程
 * （无事务、游离态实体）触发懒加载；推送失败只记日志、不影响作业主流程。
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityPublisher {

    /**
     * 全平台部署动态广播频道。
     */
    public static final String ACTIVITIES_DESTINATION = "/topic/activities";

    private final SimpMessagingTemplate messagingTemplate;
    private final DeploymentJobRepository deploymentJobRepository;

    /**
     * 推送指定作业的最新动态快照。查不到（已删除等）或推送失败均静默降级，不影响主流程。
     */
    public void publish(String jobId) {
        try {
            deploymentJobRepository.findActivityByJobId(jobId)
                    .ifPresent(activity -> messagingTemplate.convertAndSend(ACTIVITIES_DESTINATION, activity));
        } catch (Exception e) {
            log.warn("推送部署动态失败: jobId=[{}]", jobId, e);
        }
    }
}
