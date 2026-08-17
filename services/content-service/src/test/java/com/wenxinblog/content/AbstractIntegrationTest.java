package com.wenxinblog.content;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 集成测试基类：单例 PostgreSQL + MinIO 容器（minio/minio 镜像与 compose 同源）。
 * 上传/删除走真实 MinIO round-trip，metadata 走真实 PG（Flyway 建表）。
 *
 * <p>与 blog/rec 相同的两个坑：
 * ① 自定义 FlywayConfig @Value 读 spring.flyway.url，r2dbc/flyway 两套 URL 都要显式指向容器；
 * ② Flyway 11 baselineOnMigrate=true 对空库也会 baseline 到 1（跳过 V1），需静态块手动预建 V1 表。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("blog_db")
                    .withUsername("postgres")
                    .withPassword("postgres");

    /** root 凭证 minioadmin/minioadmin 与 application.yml 的 @Value 默认值一致，免属性覆盖。 */
    protected static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
                    .withExposedPorts(9000)
                    .withEnv("MINIO_ROOT_USER", "minioadmin")
                    .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
                    .withCommand("server", "/data");

    static {
        // Docker 29+ 最低 API v1.40，Testcontainers 自带 docker-java 默认请求 v1.32 会被 400 拒绝
        System.setProperty("api.version", System.getProperty("api.version", "1.44"));
        POSTGRES.start();
        MINIO.start();
        applyV1SchemaLikeDevEnvironment();
    }

    /** 手动执行 V1 建表，复现 dev 库状态（表已存在、无 flyway 历史），之后 Flyway baseline 并跳过 V1。 */
    private static void applyV1SchemaLikeDevEnvironment() {
        String sql;
        try (var in = AbstractIntegrationTest.class.getResourceAsStream("/db/migration/V1__init_content_schema.sql")) {
            sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取 V1 迁移脚本失败", e);
        }
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            for (String stmt : sql.split(";")) {
                String s = stmt.replaceAll("--[^\\n]*", "").trim();
                if (!s.isEmpty()) st.execute(s);
            }
        } catch (Exception e) {
            throw new IllegalStateException("初始化测试库 schema 失败", e);
        }
    }

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://%s:%d/%s"
                .formatted(POSTGRES.getHost(), POSTGRES.getFirstMappedPort(), POSTGRES.getDatabaseName()));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        // MinIO 客户端与 ensureMinioBucket 指向容器（凭证默认值已对齐）
        registry.add("minio.endpoint", () -> "http://%s:%d".formatted(MINIO.getHost(), MINIO.getMappedPort(9000)));
    }

    /** 每个用例前清空业务表（不动 flyway 历史），保证用例间隔离。 */
    @BeforeEach
    void truncateBusinessTables() throws Exception {
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            st.execute("TRUNCATE media_assets, media_variants CASCADE");
        }
    }

    protected static Connection openJdbc() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
