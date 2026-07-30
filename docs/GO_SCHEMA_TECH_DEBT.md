# Go 服务 Schema 管理技术债

> 记录于 2026-07-31。auth-service / user-service 的数据库 schema 暂时手动管理，待后续补齐自动化。

## 现状

auth-service、user-service（Go）目前**没有任何 schema 版本化机制**：

- 无 migration 工具（仅 `database/sql` + `github.com/lib/pq`）
- 代码里无建表逻辑（无 `CREATE TABLE`、无 ORM AutoMigrate）
- 表（`users` / `follows` / `user_profiles` / `user_stats`）是早期手动 `psql` 建在容器里的
- `docker-compose.yml` 里 postgres-auth / postgres-user **只挂了 data volume，没有 init SQL 挂载**
- 建表 SQL 此前**根本不在仓库**

## 风险

- **容器重建 / data volume 清掉 → 表结构永久丢失**，且无处自动恢复
- 多环境部署需要手动重复建表，易遗漏/不一致
- schema 变更无版本追踪，无法回滚

## 当前缓解（已完成）

表结构已 `pg_dump --schema-only` 导出到仓库，至少进版本控制：

- `services/auth-service/db/schema.sql`
- `services/user-service/db/schema.sql`

容器丢失后可手动恢复：

```bash
docker exec -i wenxinblog-postgres-auth psql -U postgres -d auth_db < services/auth-service/db/schema.sql
docker exec -i wenxinblog-postgres-user  psql -U postgres -d user_db  < services/user-service/db/schema.sql
```

⚠️ 但这仍是**手动**流程，没有自动迁移、没有版本追踪。schema 变更时需手动同步更新这两个文件。

## 全项目 schema 管理全景

| 服务                                              | 语言   | schema 管理方式                                                                                                  |
| ------------------------------------------------- | ------ | ---------------------------------------------------------------------------------------------------------------- |
| blog / content / recommendation / ad / experiment | Java   | **Flyway**（显式 FlywayConfig，独立 history table；共享 blog_db 的 4 服务用 `flyway_schema_history_<svc>` 隔离） |
| **auth / user**                                   | **Go** | **手动（本文档所述技术债）**，schema.sql 进仓库兜底                                                              |
| search                                            | Java   | Elasticsearch 索引（无 PG schema）                                                                               |
| analytics                                         | Java   | ClickHouse 表（事件存储）                                                                                        |

## 后续方案

推荐 **[golang-migrate](https://github.com/golang-migrate/migrate)**（Go 生态标准，支持 PostgreSQL），与 Java 侧 Flyway 对等——各自语言各自管，但都有版本化迁移：

- migration SQL 文件（`services/auth-service/db/migrations/0001_init.up.sql` + `.down.sql`）
- 启动时嵌入执行，或独立的 migrate CLI
- 支持版本追踪、回滚、多环境一致

备选：

- **goose**：类似，还支持用 Go 函数写 migration
- **启动时 `go:embed` schema.sql 执行**：最简，但无版本追踪，仅适合 schema 已稳定的阶段

## 何时必须解决

- 生产部署前（当前最大的 blocker）
- 上 CI/CD 自动化部署时
- auth/user 的表结构开始频繁变更时
