# 部署指南

> 最近更新：2026-08-02（对照实际配置核对）

## 本地开发

### 前置要求

- Docker Desktop 4.x+
- Java 25+（Java 服务）+ Maven 3.9+
- Go 1.23+（auth / user 服务）
- Node.js 20+（web 前端）

### 一键脚本（推荐）

本地开发统一用 `scripts/start-dev.sh`，它负责：启 docker 基建 → 跑全部应用服务（含 OTel 注入、限堆）→ 跑 web 前端 → 启网关 watchdog。

```bash
cd wenxinBlog
./scripts/start-dev.sh start    # 全量启动
./scripts/start-dev.sh status   # 查看各服务状态
./scripts/start-dev.sh stop     # 只停项目服务，保留 docker 基建
./scripts/start-dev.sh restart  # stop + start
```

脚本要点（对应 `start-dev.sh` 实现）：

- **基建只起 docker-compose.yml**：用 `-f docker-compose.yml` 显式只起主 compose，**不带** `docker-compose.override.yml`（override 里是应用服务的 build 定义，项目服务原生跑，带上会被 buildkit 卡住）。
- **应用服务原生跑**（非容器）：Go 服务 `go run ./cmd/server`，Java 服务 `mvn spring-boot:run`，Python 服务 `opentelemetry-instrument uvicorn`。
- **OTel 注入**：Java 用 `-javaagent:infra/otel/opentelemetry-javaagent.jar`（v2.30.0，首次自动下载）；Go 用 OTel SDK 读 `OTEL_*` 环境变量；统一上报到宿主机 `localhost:4317`（gRPC）。
- **Java 堆统一 512m**：所有 Java 服务（blog/search/recommend/experiment/analytics/content/ad/gateway）`-Xmx512m`，防 OOM。
- **网关 watchdog**：后台每 30s 探 `http://localhost:8080/health`，挂了自动重启 gateway（含 OTel agent + env）。
- **stop 行为**：只杀项目服务（按端口）+ web，**保留** docker 基建（postgres/redis/kafka/ES/clickhouse 等），便于快速迭代。

### 服务端口一览

| 服务                   | 语言    | 端口 | 启动方式    |
| ---------------------- | ------- | ---- | ----------- |
| auth-service           | Go      | 8001 | go run      |
| user-service           | Go      | 8002 | go run      |
| blog-service           | Java    | 8003 | mvn (512m)  |
| content-service        | Java    | 8004 | mvn (512m)  |
| search-service         | Java    | 8005 | mvn (512m)  |
| recommendation-service | Java    | 8006 | mvn (512m)  |
| ad-service             | Java    | 8007 | mvn (512m)  |
| experiment-service     | Java    | 8009 | mvn (512m)  |
| analytics-service      | Java    | 8010 | mvn (512m)  |
| gateway                | Java    | 8080 | mvn (512m)  |
| web                    | Next.js | 3000 | npm run dev |

> embedding-service 是独立仓（`../embedding-service`，端口 8008），脚本会尝试拉起，不在本仓管理范围内。

### 手动起单个服务（调试用）

```bash
# 只起基建（不跑项目服务）
docker compose -f docker-compose.yml up -d

# 单独跑某个服务（记得先 export OTEL_*，见 start-dev.sh 的 export_otel_env）
cd services/blog-service
mvn -Dspring-boot.run.jvmArguments="-Xmx512m -javaagent:$PWD/infra/otel/opentelemetry-javaagent.jar" spring-boot:run
```

### 验证基建

```bash
# PostgreSQL（4 个实例）
docker exec wenxinblog-postgres-auth psql -U postgres -c "SELECT 1"

# Redis
docker exec wenxinblog-redis redis-cli -a redis ping

# Elasticsearch（全文检索 + OTel traces/logs 存储；已从 OpenSearch 迁移）
curl -s http://localhost:9200/_cluster/health | jq .status

# Milvus
curl http://localhost:9091/healthz

# OTel Collector
curl http://localhost:8889/metrics | head        # metrics exporter
# Kafka UI
open http://localhost:8085
# Grafana
open http://localhost:3001   # admin/admin
```

## 生产部署（阿里云，尚未实施）

> **现状**：目前只有本地 dev 部署（docker 基建 + 原生进程）。以下生产架构是**目标设计**，
> K8s 容器化部署是后期任务，尚未落地。本节作为规划参考保留。

### 架构

```
                    ┌─────────────────┐
                    │   SLB / CDN     │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │   API Gateway   │
                    │  (ECS/ACK)      │
                    └────────┬────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
┌───────▼──────┐    ┌────────▼────────┐   ┌──────▼──────┐
│ Auth Service │    │  Blog Service   │   │Content Svc  │
│  (ECS/Pod)   │    │   (ECS/Pod)     │   │  (ECS/Pod)  │
└──────────────┘    └─────────────────┘   └─────────────┘
        │                    │                    │
┌───────▼──────┐    ┌────────▼────────┐   ┌──────▼──────┐
│  auth_db     │    │   blog_db       │   │ OSS         │
│ (RDS/PriDB)  │    │  (RDS/PriDB)    │   │ (OSS)       │
└──────────────┘    └─────────────────┘   └─────────────┘
```

### 服务部署

#### 1. 容器化

```bash
# 构建镜像
docker build -t registry.cn-hangzhou.aliyuncs.com/wenxinblog/auth-service:latest services/auth-service
docker build -t registry.cn-hangzhou.aliyuncs.com/wenxinblog/blog-service:latest services/blog-service

# 推送镜像
docker push registry.cn-hangzhou.aliyuncs.com/wenxinblog/auth-service:latest
```

#### 2. Kubernetes部署

> 待实施。当前本地 dev 用 docker-compose（基建）+ 原生进程（应用），K8s 是后期任务。

```yaml
# services/auth-service/k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: auth-service
  template:
    metadata:
      labels:
        app: auth-service
    spec:
      containers:
        - name: auth-service
          image: registry.cn-hangzhou.aliyuncs.com/wenxinblog/auth-service:latest
          ports:
            - containerPort: 8001
          env:
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: db-secret
                  key: auth-url
          resources:
            requests:
              memory: '256Mi'
              cpu: '250m'
            limits:
              memory: '512Mi'
              cpu: '500m'
```

#### 3. 数据库部署

- RDS PostgreSQL 15 (主从高可用，4 个库：auth/user/blog/experiment)
- Redis 企业版 (集群模式)
- 阿里云 Elasticsearch（全文检索 + 可观测性 traces/logs，对齐本地栈）

#### 4. 监控

- Prometheus + Grafana
- 阿里云云监控
- 链路追踪 (ARMS)

### CI/CD

```yaml
# .github/workflows/deploy.yml
name: Deploy
on:
  push:
    tags: ['v*']
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build & Push
        run: |
          docker build -t $IMAGE .
          docker push $IMAGE
      - name: Deploy to ACK
        run: kubectl set image deployment/auth-service auth-service=$IMAGE
```

## 环境变量

生产环境变量应使用K8s Secret或阿里云密钥管理服务:

```bash
# 敏感信息
JWT_SECRET, DATABASE_URL, REDIS_PASSWORD

# OAuth
GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET

# 阿里云
OSS_ACCESS_KEY_ID, OSS_ACCESS_KEY_SECRET
```
