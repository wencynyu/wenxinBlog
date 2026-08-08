# WenxinBlog 项目进展总结

> 最后更新：2026-07-27 | 74 commits | ~23,600 行代码 | GitHub: [wencynyu/wenxinBlog](https://github.com/wencynyu/wenxinBlog)

---

## 一、已完成（生产可用）

### 1. 推荐系统（完整三阶段 + 多模态）

| 阶段        | 内容                                        | 技术栈                                    |
| ----------- | ------------------------------------------- | ----------------------------------------- |
| **Phase 1** | 真实热门（blog_db 互动分 × 时间衰减）       | PostgreSQL 加权 SQL                       |
| **Phase 2** | Milvus 向量内容相似（/related + /feed）     | Milvus 2.4 + Qwen3-VL-Embedding-2B（MLX） |
| **Phase 3** | 用户兴趣向量 + 混合排序 + 行为闭环 + 多模态 | EMA 画像 + Redis 去重 + VL 图文混合检索   |

**推荐管线全链路：**

```
用户看帖/点赞 → /feedback 行为事件 → Kafka → BehaviorEventConsumer
→ 兴趣标签 + 帖子向量 EMA → user_embeddings 更新 → /feed 个性化重排
→ 混合分(0.6×相似 + 0.3×热度 + 0.1×新鲜) → 过滤已看 → 返回
```

### 2. 可观测性（Prometheus + Grafana）

| 维度         | 指标                                                     | 覆盖服务                     |
| ------------ | -------------------------------------------------------- | ---------------------------- |
| **JVM**      | Heap/NonHeap 内存、GC 暂停、线程数、CPU                  | blog / rec / gateway（Java） |
| **HTTP**     | QPS（按端点）、P90/P99 延迟直方图                        | 全部 5 服务（Java + Go）     |
| **业务**     | embedding 延迟/成功率/熔断、Milvus 搜索/写入延迟         | recommendation-service       |
| **业务**     | 推荐来源分布(personalized/trending/fallback)、缓存命中率 | recommendation-service       |
| **业务**     | 用户向量更新频率、backfill 进度                          | recommendation-service       |
| **基础设施** | 网关熔断器状态(Resilience4j)、服务健康                   | gateway + 全部               |

- **Prometheus**：5 个服务 scrape（15s 间隔）
- **Grafana**：2 个自动加载 dashboard（`localhost:3001`，admin/admin）
  - "WenxinBlog 服务监控"：JVM + HTTP + CPU + 健康（8 面板）
  - "WenxinBlog API + 推荐指标"：QPS/P90/P99 + embedding/Milvus/推荐来源/缓存（12 面板）

### 3. 微服务架构（10 服务 + 独立 embedding 服务）

| 服务                   | 语言                           | 端口      | 状态                                 | 测试数 |
| ---------------------- | ------------------------------ | --------- | ------------------------------------ | ------ |
| auth-service           | Go + Fiber                     | 8001      | ✅ JWT + RBAC + Google OAuth/短信    | 57     |
| user-service           | Go + Fiber                     | 8002      | ✅ 用户 CRUD + profile 懒创建 + 关注 | 107    |
| blog-service           | Java 25 + Spring Boot 4        | 8003      | ✅ 博文 CRUD + Kafka 事件 + 批量填充 | 44     |
| content-service        | Java 25 + Spring Boot 4        | 8004      | ✅ 文件上传（MinIO/OSS）             | —      |
| search-service         | Java 25 + Spring Boot 4        | 8005      | ✅ Elasticsearch 全文检索 + IK 分词  | 76     |
| recommendation-service | Java 25 + Spring Boot 4        | 8006      | ✅ Milvus 向量推荐 + 行为画像        | 38     |
| ad-service             | Java 25 + Spring Boot 4        | 8007      | ✅ 广告追踪（Kafka）                 | —      |
| experiment-service     | Java 25 + Spring Boot 4        | 8009      | ✅ A/B 实验（分层正交）              | —      |
| analytics-service      | Java 25 + Spring Boot 4        | 8010      | ✅ 行为分析 BI（ClickHouse）         | —      |
| gateway                | Java 25 + Spring Cloud Gateway | 8080/8081 | ✅ 路由 + JWT 鉴权 + 限流 + 熔断     | 49     |
| **embedding-service**  | Python + MLX                   | 8008      | ✅ Qwen3-VL-Embedding-2B（mxfp8）    | —      |

### 4. 基础设施

| 组件          | 用途                                          |
| ------------- | --------------------------------------------- |
| PostgreSQL ×3 | auth_db / user_db / blog_db                   |
| Redis         | 推荐缓存 + 用户已看去重                       |
| Elasticsearch | 全文搜索 + 自动补全                           |
| Milvus 2.6    | 向量检索（blog_embeddings + user_embeddings） |
| Kafka         | 博文生命周期事件 + 用户行为事件               |
| MinIO         | 对象存储（封面图/附件）                       |

### 5. 前端（Next.js 14 + Semi-Design）

- 首页（无限滚动） / 推荐（个性化 RecommendationCard） / 热门 / 博文列表（排序+标签筛选）
- 博文详情（Markdown 渲染 + 代码高亮 + 相关推荐 + 评论）
- 编辑器（Markdown + 封面图上传 + 标签）
- 搜索 / 设置（兴趣标签） / 个人主页（/user/[id]）
- 响应式布局 + 暗色模式 + 骨架屏

### 6. 安全

- JWT 鉴权（网关 AuthenticationFilter 注入 X-User-Id，GET 可空匿名、POST 强制）
- userId 从 JWT 注入（不可伪造客户端参数）
- embedding 熔断器（连续失败 5 次 → 冷却 30s → 降级 trending）

---

## 二、测试覆盖率

| 服务                   | 覆盖率 | 达标(75%)               |
| ---------------------- | ------ | ----------------------- |
| ad-service             | 92.6%  | ✅                      |
| gateway                | 85.0%  | ✅                      |
| search-service         | 82.5%  | ✅                      |
| content-service        | 73.2%  | ❌ 差 1.8%              |
| blog-service           | 58.9%  | ❌（需 Testcontainers） |
| recommendation-service | 28.2%  | ❌（需 Testcontainers） |

**差距原因**：blog + rec 的 reactive DatabaseClient 链（listPublishedPosts / saveTags / fillAuthorAndTags）需要真实 DB 才能有意义地覆盖。纯 mock 极其脆弱且价值低。

---

## 三、后续规划

### 🔴 高优先级

| #   | 任务                                 | 预估 | 价值                                                              |
| --- | ------------------------------------ | ---- | ----------------------------------------------------------------- |
| 1   | **CI/CD（GitHub Actions）**          | ~2h  | push 自动测试 + 构建，"工程项目"的分水岭                          |
| 2   | **测试覆盖率补齐（Testcontainers）** | ~1d  | blog + rec 到 75%，满足 proj-desc 要求                            |
| 3   | **Docker 化全栈部署**                | ~4h  | 8 服务 Dockerfile + compose 编排，一条 `docker compose up` 起全栈 |

### 🟡 中优先级

| #   | 任务                                                 | 预估  | 价值                                       |
| --- | ---------------------------------------------------- | ----- | ------------------------------------------ |
| 4   | mobile 客户端（iOS）更新                             | ~1d   | API 已就绪，mobile 还用旧接口              |
| 5   | content-service 完善（图片压缩/视频转码/CDN）        | ~半天 | 目前只有简单文件上传                       |
| 6   | SEO 优化（sitemap / robots / SSR meta / 结构化数据） | ~2h   | proj-desc 强调                             |
| 7   | Go 服务业务指标补齐（goroutine / GC / memory）       | ~1h   | fiberprometheus 默认不暴露 Go runtime 指标 |

### 🟢 低优先级

| #   | 任务                                                | 预估  | 价值                              |
| --- | --------------------------------------------------- | ----- | --------------------------------- |
| 8   | user-to-user 协同过滤（"可能认识的人"）             | ~半天 | getUserRecommendations 当前返回空 |
| 9   | 2FA（proj-desc 提到，作为可选项）                   | ~半天 |                                   |
| 10  | 广告系统接入（ad-service 目前只追踪不投放）         | ~1d   |                                   |
| 11  | Grafana 告警规则（延迟/错误率/熔断触发 Slack/邮件） | ~2h   |                                   |

---

## 四、技术债

| 债务                                                    | 影响                     | 建议                                          |
| ------------------------------------------------------- | ------------------------ | --------------------------------------------- |
| listPostsByAuthor 仍有 N+1（保留 per-post fill 供测试） | 作者主页帖子多时慢       | 后续用 batch 填充                             |
| search-service 双索引已消除但残留代码                   | 无功能影响               | 可删 BlogSearchRepository 旧方法              |
| Go 服务 fiberprometheus 指标名与 Java 不同              | Dashboard 需要 `or` 查询 | 可统一用 Prometheus relabel 改名              |
| embedding-service 不在 Docker（MLX 限制）               | 部署需额外步骤           | 生产用 vLLM 容器（compose profiles:gpu 已配） |
| `blog/` 空目录（遗留 target/）                          | 无影响                   | 可删                                          |
| start-dev.sh Python 服务首次 pip install 较慢           | 首次启动体验             | 可预构建 venv                                 |

---

## 五、关键技术决策记录

| 决策               | 选择                                                 | 理由                                                                 |
| ------------------ | ---------------------------------------------------- | -------------------------------------------------------------------- |
| embedding 模型     | Qwen3-VL-Embedding-2B (mxfp8)                        | 中英双语 + 多模态（图文同空间），mxfp8 在 Apple Silicon 上 ~24s 加载 |
| embedding 服务     | 独立 Python（MLX），不在 wenxinBlog 仓库             | Docker-on-Mac 无 MPS/MLX；生产换 vLLM+NVIDIA                         |
| 向量维度           | 1024（Matryoshka 截断自原生 2048）                   | 存储/搜索效率 + 召回率平衡                                           |
| 事件 topic         | 复用 `wenxinblog.blog.events`（search-service 定义） | search-service + recommendation-service 各自消费组                   |
| 用户画像           | 标签加权聚合 + 帖子向量 EMA（item-CF lite）          | 冷启动用标签，行为积累后 EMA 主导                                    |
| Gateway management | 独立端口 8081                                        | Spring Cloud Gateway 路由管道会拦截 /actuator/*                      |
| Feed 去重          | Redis SET（30 天滚动窗口）                           | 无需新表，O(1) 查询                                                  |

---

_本文档由 Claude Code 生成，反映截至 2026-07-27 的项目状态。_
