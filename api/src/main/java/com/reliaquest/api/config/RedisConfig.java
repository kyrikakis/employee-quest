package com.reliaquest.api.config;

import com.reliaquest.api.model.Employee;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * Configures a ReactiveRedisTemplate with String keys and Object values,
     * using Jackson2JsonRedisSerializer for value serialization/deserialization.
     * This matches the `ReactiveRedisTemplate<String, Object>` requested by EmployeeService.
     */
    @Bean
    public ReactiveRedisTemplate<String, Employee> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<Employee> jackson2JsonRedisSerializer =
                new Jackson2JsonRedisSerializer<>(Employee.class);

        // Use the correct generic types for serializationContext
        RedisSerializationContext<String, Employee> serializationContext =
                RedisSerializationContext.<String, Employee>newSerializationContext(
                                new StringRedisSerializer()) // Key serializer
                        .value(jackson2JsonRedisSerializer) // Value serializer (for Employee <-> JSON)
                        .build();

        return new ReactiveRedisTemplate<>(factory, serializationContext);
    }

    /**
     * Secondary ReactiveRedisTemplate for String keys and String values.
     * This is specifically for ZSET operations where the member is a String (e.g., employee ID).
     * The score for ZSETs is always a double, handled at the call site.
     */
    @Bean
    public ReactiveRedisTemplate<String, String> reactiveStringRedisTemplate(ReactiveRedisConnectionFactory factory) {

        RedisSerializationContext<String, String> serializationContext =
                RedisSerializationContext.<String, String>newSerializationContext(
                                new StringRedisSerializer()) // Key serializer
                        .value(new StringRedisSerializer()) // Value (member) serializer
                        .build();

        return new ReactiveRedisTemplate<>(factory, serializationContext);
    }
}
