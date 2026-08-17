package com.wenxin.blog;

import com.wenxin.blog.service.BlogEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 集成测试基类：单例 PostgreSQL 容器（静态块启动一次，Ryuk 在 JVM 退出后回收）。
 *
 * <p>自定义 FlywayConfig 用 @Value 读 spring.flyway.url，不走 Boot 的 ConnectionDetails 自动配置，
 * 因此 r2dbc 与 flyway 两套连接都必须在 @DynamicPropertySource 里显式指向容器。
 *
 * <p>注意不能用 withInitScript 预建表：FlywayConfig 配了 baselineOnMigrate(baselineVersion=1)，
 * 非空 schema 会被 baseline 跳过 V1 建表脚本；空库才会完整执行 V1+V2。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("blog_db")
                    .withUsername("postgres")
                    .withPassword("postgres");

    static {
        // Docker 29+ 引擎最低只支持 API v1.40，而 Testcontainers 1.21.x 自带的 docker-java 默认请求 v1.32，
        // 会被引擎以 400 拒绝（"Could not find a valid Docker environment"）。显式指定兼容版本，docker CLI 无此问题。
        System.setProperty("api.version", System.getProperty("api.version", "1.44"));
        POSTGRES.start();
        applyV1SchemaLikeDevEnvironment();
    }

    /**
     * 手动执行 V1 建表脚本，复现 dev 环境的库状态（表已存在、无 flyway 历史）。
     * 原因：Flyway 11 在 baselineOnMigrate=true 时对空 schema 也会先 baseline 到版本 1（跳过 V1），
     * 直接跑会因 posts 表不存在而失败；这与 FlywayConfig 注释描述的旧行为不同。
     * 之后 Flyway 正常 baseline 并执行 V2，与 dev 环境的迁移轨迹一致。
     */
    private static void applyV1SchemaLikeDevEnvironment() {
        String sql;
        try (var in = AbstractIntegrationTest.class.getResourceAsStream("/db/migration/V1__init_blog_schema.sql")) {
            sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取 V1 迁移脚本失败", e);
        }
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            for (String stmt : sql.split(";")) {
                String s = stmt.replaceAll("--[^\\n]*", "").trim();
                if (!s.isEmpty()) st.execute(s);
            }
            // authors 是 user-service 同步的缓存表（迁移脚本未包含），一并补建
            st.execute("""
                    CREATE TABLE IF NOT EXISTS authors (
                        id UUID PRIMARY KEY,
                        username VARCHAR(100),
                        display_name VARCHAR(100),
                        avatar_url VARCHAR(500)
                    )""");
        } catch (Exception e) {
            throw new IllegalStateException("初始化测试库 schema 失败", e);
        }
    }

    /**
     * Kafka 事件发布器 mock：本地无 broker 时 KafkaTemplate.send 会阻塞等待元数据（max.block.ms 默认 60s）；
     * 事件组装逻辑已由 BlogEventPublisherTest 单测覆盖，这里只验证是否发布。
     */
    @MockitoBean
    protected BlogEventPublisher blogEventPublisher;

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://%s:%d/%s"
                .formatted(POSTGRES.getHost(), POSTGRES.getFirstMappedPort(), POSTGRES.getDatabaseName()));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    /** 每个用例前清空业务表（不动 flyway_schema_history_blog），保证用例间隔离。 */
    @BeforeEach
    void truncateBusinessTables() throws Exception {
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            st.execute("TRUNCATE posts, tags, post_tags, comments, post_likes, post_favorites, authors CASCADE");
        }
    }

    protected static Connection openJdbc() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** 直接 JDBC 插入 authors 缓存行（绕过 R2DBC 实体，模拟 user-service 同步结果）。 */
    protected static void insertAuthor(java.util.UUID id, String username, String displayName) throws Exception {
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO authors (id, username, display_name, avatar_url) VALUES ("
                    + "'" + id + "', '" + username + "', '" + displayName + "', NULL)");
        }
    }
}
