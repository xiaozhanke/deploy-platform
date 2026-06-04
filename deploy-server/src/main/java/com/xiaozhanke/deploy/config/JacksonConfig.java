package com.xiaozhanke.deploy.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Jackson 配置
 *
 * @author xiaozhanke
 */
@Configuration
public class JacksonConfig {

    // 默认时区
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    // 默认日期时间格式
    private static final String DEFAULT_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    // 默认日期格式
    private static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd";
    // 默认时间格式
    private static final String DEFAULT_TIME_PATTERN = "HH:mm:ss";
    // 默认日期时间格式化器
    private static final DateTimeFormatter DEFAULT_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_PATTERN);
    // 默认日期格式化器
    private static final DateTimeFormatter DEFAULT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern(DEFAULT_DATE_PATTERN);
    // 默认时间格式化器
    private static final DateTimeFormatter DEFAULT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DEFAULT_TIME_PATTERN);

    /**
     * Jackson 自动配置定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            // 设置时区
            builder.timeZone(TimeZone.getTimeZone(DEFAULT_ZONE_ID));

            // 配置日期时间格式化
            // 配置 LocalDateTime 序列化和反序列化
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DEFAULT_DATE_TIME_FORMATTER));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DEFAULT_DATE_TIME_FORMATTER));
            // 配置 LocalDate 序列化和反序列化
            builder.serializerByType(LocalDate.class, new LocalDateSerializer(DEFAULT_DATE_FORMATTER));
            builder.deserializerByType(LocalDate.class, new LocalDateDeserializer(DEFAULT_DATE_FORMATTER));
            // 配置 LocalTime 序列化和反序列化
            builder.serializerByType(LocalTime.class, new LocalTimeSerializer(DEFAULT_TIME_FORMATTER));
            builder.deserializerByType(LocalTime.class, new LocalTimeDeserializer(DEFAULT_TIME_FORMATTER));
            // 兼容 java.util.Date 的格式化
            builder.simpleDateFormat(DEFAULT_DATE_TIME_PATTERN);

            // 配置 ObjectMapper 的特性
            // 不使用时间戳格式
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            // 遇到未知字段不报错
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        };
    }
}
