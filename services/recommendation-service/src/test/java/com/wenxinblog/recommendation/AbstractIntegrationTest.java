package com.wenxinblog.recommendation;

import com.wenxinblog.recommendation.client.EmbeddingClient;
import com.wenxinblog.recommendation.service.MilvusInitializer;
import com.wenxinblog.recommendation.service.MilvusService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

/**
 * 集成测试基类：单例 PostgreSQL + Redis + Kafka 容器（镜像与 docker-compose 同版本）。
 *
 * <p>与 blog-service 相同的两个坑：
 * <ul>
 *   <li>自定义 FlywayConfig @Value 读 spring.flyway.url，不走 ConnectionDetails，r2dbc/flyway 两套 URL 都要显式覆盖；</li>
 *   <li>Flyway 11 baselineOnMigrate=true 对空 schema 也会 baseline 到 1（跳过 V1），
 *       因此 rec 的 V1（user_interest_tags 等）与 blog 的表结构（posts 等，来自 blog-service 的迁移）
 *       都在静态块里手动预建，之后 Flyway 只做 baseline，与 dev 库状态一致。</li>
 * </ul>
 *
 * <p>Milvus/embedding 不在此基类拉起：Milvus 有专用的真实容器测试（见 MilvusServiceIntegrationTest），
 * 这里的推荐链路测试用 {@code @MockitoBean} 桩掉 MilvusService/EmbeddingClient。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("blog_db")
                    .withUsername("postgres")
                    .withPassword("postgres");

    /** 密码与 application.yml 默认值（redis）一致，省去密码属性覆盖。 */
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withCommand("redis-server", "--requirepass", "redis");

    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    // 消费者/生产者涉及的 topic（自动建齐，不依赖 broker 的 auto.create.topics）
    private static final List<String> TOPICS = List.of("wenxinblog.blog.events", "user-behavior-events");

    @MockitoBean
    protected MilvusService milvusService;

    @MockitoBean
    protected MilvusInitializer milvusInitializer; // 避免 ApplicationRunner 在启动期打真实 Milvus

    @MockitoBean
    protected EmbeddingClient embeddingClient;

    static {
        // Docker 29+ 最低 API v1.40，Testcontainers 自带 docker-java 默认请求 v1.32 会被 400 拒绝
        System.setProperty("api.version", System.getProperty("api.version", "1.44"));
        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
        applySchemas();
        createTopics();
    }

    private static void applySchemas() {
        executeScript("/db/migration/V1__init_recommendation_schema.sql");
        executeScript("/init/blog-schema.sql");
    }

    private static void executeScript(String classpath) {
        String sql;
        try (var in = AbstractIntegrationTest.class.getResourceAsStream(classpath)) {
            sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取 schema 脚本失败: " + classpath, e);
        }
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            for (String stmt : sql.split(";")) {
                String s = stmt.replaceAll("--[^\\n]*", "").trim();
                if (!s.isEmpty()) st.execute(s);
            }
        } catch (Exception e) {
            throw new IllegalStateException("执行 schema 脚本失败: " + classpath, e);
        }
    }

    private static void createTopics() {
        try (AdminClient admin = AdminClient.create(
                java.util.Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(TOPICS.stream()
                            .map(t -> new NewTopic(t, 1, (short) 1)).toList())
                    .all().get();
        } catch (Exception e) {
            throw new IllegalStateException("创建 Kafka topic 失败", e);
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
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    /** 每个用例前清空业务表与 Redis（不动 flyway 历史），保证用例隔离。 */
    @BeforeEach
    void cleanupStores() throws Exception {
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            st.execute("TRUNCATE posts, tags, post_tags, authors, user_interest_tags, recommendation_config CASCADE");
        }
        var flush = REDIS.execInContainer("redis-cli", "-a", "redis", "FLUSHALL");
        if (flush.getExitCode() != 0) {
            throw new IllegalStateException("Redis FLUSHALL 失败: " + flush.getStderr());
        }
    }

    protected static Connection openJdbc() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** JDBC 插入已发布帖子（推荐流/trending 的种子数据），返回 postId。 */
    protected static UUID insertPublishedPost(UUID id, UUID authorId, String title, int likeCount,
                                              int viewCount, int commentCount, java.time.LocalDateTime publishedAt) throws Exception {
        try (Connection conn = openJdbc();
             var ps = conn.prepareStatement(
                     "INSERT INTO posts (id, author_id, title, content, summary, status, like_count, view_count, comment_count, published_at, created_at, updated_at) "
                             + "VALUES (?, ?, ?, '正文', '摘要', 'published', ?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, authorId);
            ps.setString(3, title);
            ps.setInt(4, likeCount);
            ps.setInt(5, viewCount);
            ps.setInt(6, commentCount);
            ps.setObject(7, publishedAt);
            ps.setObject(8, publishedAt);
            ps.setObject(9, publishedAt);
            ps.executeUpdate();
        }
        return id;
    }

    protected static void insertAuthor(java.util.UUID id, String username) throws Exception {
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO authors (id, username, display_name) VALUES ('" + id + "', '" + username + "', '" + username + "')");
        }
    }
}
