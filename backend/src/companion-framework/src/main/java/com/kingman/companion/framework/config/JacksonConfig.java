package com.kingman.companion.framework.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局配置
 *
 * <p>解决 LocalDateTime 序列化格式问题：
 * Spring Boot 的 {@code date-format} 属性只对 {@code java.util.Date} 生效，
 * 对 JSR-310 的 {@code LocalDateTime} 无效。
 * 本配置通过 {@link Jackson2ObjectMapperBuilderCustomizer} 为 LocalDateTime
 * 注册统一的序列化/反序列化格式：{@value DATETIME_PATTERN}。
 *
 * <p>该格式与前端约定一致（见 api-schema.yaml：时间格式为 "yyyy-MM-dd HH:mm:ss"）。
 */
@Configuration
public class JacksonConfig {

    public static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern(DATETIME_PATTERN);

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        return builder -> builder
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .serializers(new LocalDateTimeSerializer(DATETIME_FORMATTER))
                .deserializers(new LocalDateTimeDeserializer(DATETIME_FORMATTER));
    }
}
