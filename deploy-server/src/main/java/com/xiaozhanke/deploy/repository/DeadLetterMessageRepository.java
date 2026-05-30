package com.xiaozhanke.deploy.repository;

import com.xiaozhanke.deploy.model.entity.DeadLetterMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * 死信消息持久化接口
 *
 * @author xiaozhanke
 */
@Repository
public interface DeadLetterMessageRepository extends JpaRepository<DeadLetterMessage, String>,
        JpaSpecificationExecutor<DeadLetterMessage> {

    /**
     * 死信落库幂等:同一作业的死信只记一次,避免 MQ 至少一次投递造成重复死信记录。
     */
    boolean existsByJobId(String jobId);
}
