package com.wenxinblog.content;

import org.junit.jupiter.api.Test;

/**
 * 上下文冒烟测试：Testcontainers PostgreSQL + MinIO 上真实生效——
 * Flyway 迁移、MinioClient 装配、ensureMinioBucket（容器里无 bucket → 走 makeBucket 创建路径）。
 */
class ContentApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
