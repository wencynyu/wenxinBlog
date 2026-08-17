package com.wenxin.blog;

import org.junit.jupiter.api.Test;

/**
 * 上下文冒烟测试：跑在 Testcontainers PostgreSQL 上（Flyway 迁移 + R2DBC 自动配置真实生效），
 * 不再依赖本地 5434 端口的 compose 环境。
 */
class BlogApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
