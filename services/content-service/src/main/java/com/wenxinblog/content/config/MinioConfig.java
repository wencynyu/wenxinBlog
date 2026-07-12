package com.wenxinblog.content.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${minio.secret-key:minioadmin}")
    private String secretKey;

    @Value("${minio.bucket:wenxinblog-content}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        log.info("MinIO client → endpoint={}, bucket={}", endpoint, bucket);
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public CommandLineRunner ensureMinioBucket(MinioClient client) {
        return args -> {
            try {
                boolean exists = client.bucketExists(
                        BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    client.makeBucket(
                            MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("Created MinIO bucket: {}", bucket);
                } else {
                    log.info("MinIO bucket ready: {}", bucket);
                }
            } catch (Exception e) {
                log.warn("Failed to ensure MinIO bucket '{}': {}", bucket, e.getMessage());
            }
        };
    }
}
