package com.wenxinblog.ad.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory factory) {
        StringRedisSerializer serializer = new StringRedisSerializer();
        return new ReactiveStringRedisTemplate(factory,
                org.springframework.data.redis.serializer.RedisSerializationContext
                        .<String, String>newSerializationContext(serializer)
                        .key(serializer).value(serializer)
                        .hashKey(serializer).hashValue(serializer)
                        .build());
    }
}
