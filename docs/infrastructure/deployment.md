# 部署指南

## 本地开发

### 前置要求

- Docker Desktop 4.x+
- Java 25+
- Go 1.23+
- Node.js 20+
- Maven 3.9+

### 启动基础设施

```bash
cd wenxinBlog
docker-compose up -d
```

验证服务:
```bash
# PostgreSQL
docker exec wenxinblog-postgres-auth psql -U postgres -c "SELECT 1"

# Redis
docker exec wenxinblog-redis redis-cli -a redis ping

# OpenSearch
curl http://localhost:9200

# Milvus
curl http://localhost:9091/healthz
```

### 启动后端服务

```bash
# 认证服务 (Go)
cd services/auth-service
go run cmd/server/main.go

# 用户服务 (Go)
cd services/user-service
go run cmd/server/main.go

# 博文服务 (Java)
cd services/blog-service
mvn spring-boot:run

# 内容服务 (Java)
cd services/content-service
mvn spring-boot:run

# 搜索服务 (Java)
cd services/search-service
mvn spring-boot:run

# 推荐服务 (Java)
cd services/recommendation-service
mvn spring-boot:run

# 广告服务 (Java)
cd services/ad-service
mvn spring-boot:run

# 网关 (Java)
cd services/gateway
mvn spring-boot:run
```

### 启动前端

```bash
# Web端
cd web
npm install
npm run dev

# iOS端
cd mobile
npm install
npm start
# 按 'i' 打开iOS模拟器
```

## 生产部署 (阿里云)

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
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
```

#### 3. 数据库部署

- RDS PostgreSQL 15 (主从高可用)
- Redis 企业版 (集群模式)
- OpenSearch (阿里云实例)

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
