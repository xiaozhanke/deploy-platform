package com.xiaozhanke.deploy.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhanke.deploy.messaging.dto.AuditLogMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 审计模块配置(对应 MQ 方案稿场景 4)。
 *
 * <p>有意手工构建 producer/consumer 配置(而非复用 {@code spring.kafka.*} 自动装配),把
 * <b>{@code acks=all} + {@code retries=3}</b> 这些"不丢审计"的关键参数显式钉在代码里,作为面试抓手。
 * 序列化用注入的 Spring 托管 {@link ObjectMapper}(已带 JavaTimeModule),让消息体里的
 * {@code LocalDateTime} 能正确读写——spring-kafka 默认 {@code JsonSerializer} 自建的 ObjectMapper
 * 不含该模块,会在 {@code operationTime} 上抛错。
 *
 * <p>单机开发 broker 副本因子为 1,故 {@code min.insync.replicas} 实际为 1;<b>生产环境</b>应为
 * 3 broker + {@code replication.factor=3} + {@code min.insync.replicas=2},此时 {@code acks=all}
 * 才能真正提供"多数派落盘"保证(详见 ADR-0005)。
 *
 * @author xiaozhanke
 */
@Configuration
@EnableConfigurationProperties(KafkaAuditProperties.class)
public class KafkaConfig {

    /**
     * 审计 Producer 工厂:{@code acks=all} + {@code retries=3},JSON 序列化用 Spring 托管 ObjectMapper。
     */
    @Bean
    public ProducerFactory<String, AuditLogMessage> auditProducerFactory(KafkaAuditProperties properties,
                                                                          ObjectMapper objectMapper) {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // acks=all:所有 ISR 副本确认才算成功(单机 ISR=1,生产 ISR=2)
        configs.put(ProducerConfig.ACKS_CONFIG, "all");
        configs.put(ProducerConfig.RETRIES_CONFIG, 3);
        DefaultKafkaProducerFactory<String, AuditLogMessage> factory = new DefaultKafkaProducerFactory<>(configs);
        // 用 Spring 托管 ObjectMapper(含 JavaTimeModule),否则 LocalDateTime 序列化失败
        factory.setValueSerializer(new JsonSerializer<>(objectMapper));
        return factory;
    }

    @Bean
    public KafkaTemplate<String, AuditLogMessage> auditKafkaTemplate(
            ProducerFactory<String, AuditLogMessage> auditProducerFactory) {
        return new KafkaTemplate<>(auditProducerFactory);
    }

    /**
     * 审计 Consumer 工厂:JSON 反序列化锁定 {@link AuditLogMessage},信任 DTO 包。
     */
    @Bean
    public ConsumerFactory<String, AuditLogMessage> auditConsumerFactory(KafkaAuditProperties properties,
                                                                         ObjectMapper objectMapper) {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, properties.consumerGroup());
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // 关闭自动提交,改由容器在每条记录处理成功后提交(AckMode.RECORD)——At-Least-Once
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        JsonDeserializer<AuditLogMessage> valueDeserializer = new JsonDeserializer<>(AuditLogMessage.class, objectMapper);
        valueDeserializer.addTrustedPackages("com.xiaozhanke.deploy.messaging.dto");
        return new DefaultKafkaConsumerFactory<>(configs, new StringDeserializer(), valueDeserializer);
    }

    /**
     * 审计 Listener 容器工厂:并发度与分区数对齐,演示 Consumer Group 并发消费;
     * {@code AckMode.RECORD} 实现 At-Least-Once 的位移提交。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AuditLogMessage> auditKafkaListenerContainerFactory(
            ConsumerFactory<String, AuditLogMessage> auditConsumerFactory, KafkaAuditProperties properties) {
        ConcurrentKafkaListenerContainerFactory<String, AuditLogMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auditConsumerFactory);
        factory.setConcurrency(properties.partitions());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    /**
     * 审计 Topic:多分区演示并发消费;单机副本因子 1(生产应为 3)。
     */
    @Bean
    public NewTopic auditLogTopic(KafkaAuditProperties properties) {
        return TopicBuilder.name(properties.topic())
                .partitions(properties.partitions())
                .replicas(1)
                .build();
    }
}
