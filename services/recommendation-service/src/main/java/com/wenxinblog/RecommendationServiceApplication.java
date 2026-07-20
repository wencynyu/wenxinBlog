package com.wenxinblog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Recommendation Service Application
 * 推荐服务 (Milvus向量搜索)
 *
 * @EnableKafka：显式开启 @KafkaListener 注解处理（注册 KafkaListenerAnnotationBeanPostProcessor）。
 * Spring Boot 4 下保险起见显式声明，确保 BlogEventConsumer / BehaviorEventConsumer 的监听容器被创建启动。
 */
@SpringBootApplication
@EnableKafka
public class RecommendationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecommendationServiceApplication.class, args);
    }
}
