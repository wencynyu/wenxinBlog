# Auth Service 认证鉴权架构（实际实现）

> 本文是认证鉴权系统的**权威流程参考**（对照实际代码核对）。配置/快速接入见 [social-login-setup.md](./social-login-setup.md)。
> 支持三种登录：**邮箱+密码**、**Google OAuth**、**手机号短信验证码**。微信登录已移除。

---

## 一、组件与职责

| 组件             | 技术                        | 端口 | 职责                                                                      |
| ---------------- | --------------------------- | ---- | ------------------------------------------------------------------------- |
| **auth-service** | Go / Fiber v2               | 8001 | 身份唯一真相源：所有登录、JWT 签发/验证、RBAC、第三方绑定。独占 `auth_db` |
| **user-service** | Go / Fiber v2               | 8002 | 资料/关注/统计；best-effort 镜像 `users` 行（注册时同步）                 |
| **gateway**      | Java / Spring Cloud Gateway | 8080 | 路由、限流、熔断；**JWT 校验委托 auth-service**；注入身份头；粗粒度 RBAC  |
| **web**          | Next.js 14                  | 3000 | 前端；token 存 localStorage + 非 httpOnly cookie                          |
| **redis**        | 7                           | 6379 | OAuth state / 中间码、短信验证码、限流计数（密码 `redis`）                |
| **postgres**     | 15                          | 5432 | `auth_db`（users + RBAC + oauth_accounts）、`user_db`（镜像）             |

> 运行形态：基础设施（pg/redis/kafka/...）跑在 docker-compose；**应用服务（auth/gateway/web）本地直接跑**（`go run` / `mvn spring-boot:run` / `next dev`）。

---

## 二、身份与 Token 模型

- **算法**：JWT HS256，唯一密钥 `JWT_SECRET`（无默认值，空则启动 fail-fast）。
- **Claims**：`{ userId, roles[], permissions[], tokenType, ...RegisteredClaims }`。
- **有效期**：access 15min / refresh 7day；`tokenType` 区分，access 才能调 API，refresh 才能刷新。
- **谁签谁验**：**只有 auth-service 持密钥**。网关**不本地验签**，通过 `GET /api/v1/auth/validate`（带 Bearer）委托 auth-service，回 `{userId, email, roles, permissions}`。
- **身份下发**：网关 `AuthenticationFilter` 校验后注入 `X-User-Id / X-User-Roles / X-User-Email / X-User-Permissions`；`default-filters` 在入口剥离客户端伪造的同名头（纵深防御）。
- **Token 存储（前端）**：access token 存 `localStorage` + 非 httpOnly cookie（cookie 供 Server Component 读）。⚠️ XSS 暴露面，属既有遗留，本次未改。

---

## 三、登录鉴权完整流程

### 3.1 邮箱 + 密码

```
注册 POST /api/v1/auth/register {email,username,password}
  → 查重(email/username) → bcrypt(cost=12) → 写 auth_db.users
  → 分配默认 "user" 角色 → best-effort 同步 user-service(POST /internal/users)
  → 201（不返回 token，前端自动 login）

登录 POST /api/v1/auth/login {email,password}
  → FindByEmail → status==ACTIVE? → bcrypt.Compare → issueTokens
  → { user, tokens:{accessToken, refreshToken, expiresIn} }

刷新 POST /api/v1/auth/refresh {refreshToken}
  → 解析(refresh) → 重新解析 roles/perms（权限变更刷新即生效） → 新 token 对
```

> 网关：`/api/v1/auth/**` 与 `/health` 为**白名单**（不挂 AuthenticationFilter，不注入身份）。

### 3.2 Google OAuth —— BFF「后端换码 + 一次性中间码」模式 ⭐

token **永不进 URL**；浏览器只拿到一次性中间码，再 POST 换 JWT。

```
① 前端「Google 登录」
   window.location = http://localhost:8080/api/v1/auth/oauth/google        (顶层导航, 公开)
② auth-service /oauth/:provider
   生成 state → 存 Redis(oauth:state:<state>, intent=login, TTL 10min, 一次性)
   Set-Cookie: oauth_state=<state> (HttpOnly, SameSite=Lax, 同源 8080)
   302 → https://accounts.google.com/o/oauth2/v2/auth?...&state=<state>
③ 用户在 Google 授权
   Google 302 → http://localhost:8080/api/v1/auth/callback/google?code&state  (公开)
④ auth-service /callback/:provider
   校验: ConsumeOAuthState(state) 存在? + provider 匹配? + login 意图下 cookie==state?(防 login-CSRF)
   Exchange: POST oauth2.googleapis.com/token(code→access_token)  ← 经 HTTPS_PROXY 出网
             GET openidconnect.googleapis.com/v1/userinfo → {sub,email,name,picture,email_verified}
   仅 email_verified=true 才保留 email（否则置空，不作为标识）
   loginOrCreate:
     a) (google, sub) 已绑定 → 登录该用户
     b) email 已验证且命中已有账号 → 自动绑定到该账号并登录（auto-link，避免邮箱唯一冲突）
     c) 否则 → 创建新用户 + oauth_accounts 绑定 + 同步 user-service
   生成中间码 → 存 Redis(oauth:code:<code>, {userId,outcome=login}, TTL 30s, 一次性)
   302 → http://localhost:3000/auth/callback/google?code=<中间码>
⑤ 前端回调页 /auth/callback/[provider]
   读 ?code → POST /api/v1/auth/oauth/exchange {code}                  (公开)
⑥ auth-service /oauth/exchange
   ConsumeIntermediateCode → issueTokens → { user, tokens }
   前端 setSession(token 入 localStorage+cookie) → 跳首页
```

### 3.3 手机号 短信验证码（免密）

```
发送 POST /api/v1/auth/phone/send-code {phone}                        (公开)
  → 校验手机号格式 → 限流(号码 1/min·5/day, IP 10/h, Redis INCR+EXPIRE)
  → 生成 6 位码 → 哈希后存 Redis(sms:code:<phone>, TTL 5min)
  → SmsSender.Send（mock 日志打印 / aliyun 手写 dysmsapi.SendSms）
  → 统一返回 200 通用消息（不泄露号码是否注册）

登录 POST /api/v1/auth/phone/login {phone, code}                       (公开)
  → ConsumeSMSCode(一次性) → 哈希比对（错码/无码统一 401，防枚举）
  → FindByPhone 命中 → 登录；未命中 → 创建纯手机号用户(生成用户名) + 默认角色 + 同步
  → issueTokens → { user, tokens }
```

### 3.4 网关鉴权与身份下发（所有受保护接口）

```
请求 → gateway.AuthenticationFilter
  ① 入口剥离客户端伪造的 X-User-*（default-filters）
  ② 白名单(/api/v1/auth/**, /health) → 直接放行，不注入身份
  ③ 取 Authorization: Bearer → GET auth-service/auth/validate
     写(GET 无 token → 匿名放行；写无 token → 401；token无效 → 401；auth不可达 → 401)
  ④ 注入 X-User-Id/Roles/Email/Permissions → 下游
  ⑤ AuthorizationFilter(@Order 20) 按 (method,path)→权限码 粗校验，缺权限 → 403
下游服务只信任 X-User-* 头（user-service/admin handler 据此做细粒度 own/any 校验）
```

### 3.5 第三方账号绑定 / 解绑（已登录用户）

> 策略：社交登录默认创建独立账号；**已验证邮箱命中已有账号时自动绑定**（auto-link）；其余情况下用户可在设置页主动绑定。

```
绑定 POST /api/v1/account/oauth/:provider/link   (鉴权: 网关注入 X-User-Id)
  → 生成 state(intent=link:<userId>) → 返回 {authUrl}
  → 前端 window.location=authUrl → 走 3.2 ④ callback(link 意图) → 绑定到当前用户(不新建)
     → 中间码 exchange 返回 {outcome:link, provider}
解绑 DELETE /api/v1/account/oauth/:provider       (鉴权)
列表 GET  /api/v1/account/oauth                   (鉴权)
```

> `/api/v1/account/**` 与 `/api/v1/admin/**` 在网关**挂 AuthenticationFilter**（注入身份）；`/api/v1/auth/**` 不挂（故 account 不能放 auth 下）。

---

## 四、RBAC

- 表：`roles`（自引用 parent_id 继承）、`permissions`(code=`resource:action[:scope]`)、`role_permissions`、`user_roles`（支持 expires_at 临时角色）。
- 解析：`resolveRolesAndPermissions` 用**递归 CTE** 沿 parent 链聚合角色与权限。
- 种子：guest→user→author→moderator→admin（5 系统角色 + 22 权限 + 矩阵）。
- 注入：角色/权限 code 写进 JWT claims；网关从 validate 回读；权限码硬编码在三处（SQL 种子 / gateway AuthorizationFilter.RULES / 各服务 handler），有漂移风险。

---

## 五、数据模型与迁移

**auth_db（自动迁移，启动时 `internal/migrate` 跑 `db/migrations/*.up.sql`）**

- `users`：id VARCHAR(36)、username、email(可空+部分唯一)、password_hash(可空)、phone(可空+部分唯一)、avatar_url、status、two_fa_*、时间戳。
- `oauth_accounts`：id、user_id、provider、provider_user_id、raw_openid(预留)、access/refresh_token、expires_at；UNIQUE(provider,provider_user_id)。
- RBAC 四表 + `schema_migrations`。
- 迁移文件：`000001` 初始 + RBAC种子、`000002` role:manage、`000003` 存量回填、`000004` oauth+phone（放宽 email/password_hash 可空、加 phone、建 oauth_accounts）。

**user_db（无迁移器，技术债）**：`db/schema.sql` 手工快照；改库需手工 ALTER + 改 schema.sql。`InternalCreateUser` 用 `NULLIF($3,'')` 把空 email→NULL（部分唯一索引允许多个无邮箱用户）。

---

## 六、关键文件

| 关注点                                  | 文件                                                                                            |
| --------------------------------------- | ----------------------------------------------------------------------------------------------- |
| 登录/注册/刷新/validate                 | `internal/handler/auth_handler.go`、`internal/service/auth_service.go`                          |
| token 签发                              | `internal/service/jwt_service.go`（HS256, access 15m/refresh 7d）                               |
| 共享签发 `issueTokens`                  | `auth_service.go`（登录/社交/手机复用）                                                         |
| Google provider（手写 net/http）        | `internal/service/oauth_provider.go`                                                            |
| OAuth 编排(state/中间码/auto-link/绑定) | `internal/service/oauth_service.go`                                                             |
| OAuth 账号 repo                         | `internal/repository/oauth_repository.go`                                                       |
| OAuth 路由(公开+account鉴权)            | `internal/handler/oauth_handler.go`                                                             |
| 短信(mock/aliyun 手写签名)              | `internal/service/sms_sender.go`                                                                |
| 手机号 service                          | `internal/service/phone_service.go`                                                             |
| Redis(state/中间码/验证码/限流)         | `internal/cache/redis.go`                                                                       |
| 网关鉴权                                | gateway `filter/AuthenticationFilterGatewayFilterFactory.java`、`AuthorizationFilter.java`      |
| 前端                                    | web `app/(auth)/login`、`app/auth/callback/[provider]`、`lib/api/auth.ts`、`store/authStore.ts` |

---

## 七、踩坑汇总（重点）⭐

> 每条：**现象 / 根因 / 解法**。本次接入 Google 登录逐一踩过。

1. **离线环境拉不到 Go 依赖**
   - 现象：`go get golang.org/x/oauth2` → `proxy.golang.org i/o timeout`。
   - 根因：开发机不能直连 Go module proxy。
   - 解法：能从缓存复用的（go-redis、miniredis）手动加 `require` + `GOPROXY=off go mod tidy`；不能的**手写**——Google OAuth 用 `net/http`，阿里云短信手写 HMAC-SHA1 签名调 `dysmsapi`（不引 SDK）。

2. **新代码强制依赖 Redis，旧的不依赖**
   - 现象：升级后 auth-service 启动 fail-fast「redis ping failed」。
   - 根因：OAuth state/中间码、短信验证码、限流都用 Redis；`main.go` Ping fail-fast。
   - 解法：必须提供可达 Redis；密码是 `redis`，故 `REDIS_URL=redis://:redis@localhost:6379`（不带密码会 NOAUTH）。

3. **`Cannot GET /api/v1/auth/oauth/google`**
   - 现象：点登录报 Cannot GET。
   - 根因：auth-service 跑的是**旧二进制**（没有 OAuth 路由），Fiber 默认 404 就是 "Cannot GET"。
   - 解法：重新编译（`go build -o server ./cmd/server`）+ 重启。

4. **provider 回调地址必须指向「后端经网关」而非前端**
   - 现象：`redirect_uri_mismatch` 或前端 404。
   - 根因：BFF 模式下后端要完成 code→token 换取，Google 必须回调到后端。
   - 解法：`GOOGLE_REDIRECT_URL=http://localhost:8080/api/v1/auth/callback/google`，且与 Google Console「授权重定向 URI」**完全一致**。

5. **GFW：auth-service 连不上 Google（10s 超时）**
   - 现象：callback 固定耗时 10s 后 302 到失败页；curl Google 超时。
   - 根因：浏览器走系统代理能访问 Google，但 Go 进程不自动读系统代理。
   - 解法：给 auth-service 设 `HTTPS_PROXY=http://127.0.0.1:6789`（系统代理端口，`scutil --proxy` 可查）；**同时设 `NO_PROXY=localhost,127.0.0.1,::1`**，否则 user-service 同步等本地调用也被代理拖垮。

6. **邮箱唯一约束冲突（社交用户撞已有账号）**
   - 现象：`duplicate key value violates unique constraint "users_email_unique"` → 前端「授权失败」。
   - 根因：原「手动绑定」策略对已注册邮箱社交登录直接建号，撞唯一索引。
   - 解法：改 `loginOrCreate` 为 **已验证邮箱命中已有账号 → 自动绑定并登录**（auto-link）。Google 仅 `email_verified=true` 才回填 email，接管风险可控。

7. **React StrictMode 双触发，把成功结果覆盖掉**
   - 现象：日志有 `LOGIN SUCCESS / 200`，但用户仍报「网络错误/失败」被踢回登录页。
   - 根因：dev 下 `useEffect` 执行两次 → 同一中间码兑换两次 → 第二次 401（码已消费）→ 触发 axios 全局 401 拦截器 `clearToken + 跳/login`，覆盖首次成功。
   - 解法：回调页用**模块级 `Set` 按 code 去重**（state 跨不了 StrictMode remount）；成功后 `window.location.href='/'` 硬跳转重载。

8. **`lsof -ti tcp:PORT | xargs kill` 误杀网关**
   - 现象：停 auth-service 时把 gateway 也杀了。
   - 根因：不带 `-sTCP:LISTEN` 会匹配**所有**占用该端口的进程，包括 gateway 到 auth-service 的 validate 连接。
   - 解法：用 `lsof -ti tcp:8001 -sTCP:LISTEN` 只匹配监听进程；或对自己的后台任务用 `TaskStop`。

9. **阿里云 `dypnsapi` ≠ 短信 `dysmsapi`**
   - 现象：贴官方示例代码想接短信，结果对不上。
   - 根因：`dypnsapi`(号码认证/一键登录) 与 `dysmsapi`(短信服务) 是两个产品；且 SDK 依赖离线拉不到。
   - 解法：短信用 `dysmsapi.SendSms`，手写签名调用；号码认证/一键登录是另外的 feature。

10. **白名单路由无身份，绑定端点不能放 `/auth` 下**
    - 现象：若把 bind/unlink 放 `/api/v1/auth/**`，拿不到调用者身份。
    - 根因：网关白名单不挂 AuthenticationFilter、不注入 X-User-*；且 OAuth link 回调是顶层导航，浏览器不带 Bearer。
    - 解法：新增网关路由 `/api/v1/account/**`（挂 AuthenticationFilter）；鉴权在「AJAX 发起 link」这一步，回调靠一次性 state nonce。

11. **state CSRF：login 与 link 的防护不同**
    - login：发起是未鉴权导航 → 用 Redis nonce **+ oauth_state cookie 双校验**（防 login-CSRF）。
    - link：发起已鉴权（Bearer）→ 仅 Redis nonce(intent=link:<uid>) 即可。

12. **JWT_SECRET 变更使旧 token 失效**
    - 现象：重启 auth-service 后浏览器登录态丢失。
    - 根因：HS256 单密钥，换密钥则旧 token 全失效。
    - 解法：dev 可接受；正式环境固定 JWT_SECRET，勿每次随机生成。

13. **token 存 localStorage + 非 httpOnly cookie**
    - 既有遗留的 XSS 暴露面。本次未改；后续可升级为「后端 set httpOnly cookie + `/auth/refresh` 静默续期」。

14. **fire-and-forget 同步会漂库**
    - 注册/社交/手机建号后同步 user-service 是 best-effort（失败只 log）。user_db 暂无行 → profile 查询 404，直至同步成功。v1 接受。

---

## 八、本地运行要点

```bash
# 基础设施（docker，含 redis 密码 redis、pg 密码 postgres）
docker compose up -d

# auth-service（新代码依赖 redis + 代理出网）
cd services/auth-service && go build -o server ./cmd/server
JWT_SECRET=<强随机> \
REDIS_URL=redis://:redis@localhost:6379 \
DATABASE_URL=postgres://postgres:postgres@localhost:5432/auth_db?sslmode=disable \
FRONTEND_URL=http://localhost:3000 \
GOOGLE_CLIENT_ID=... GOOGLE_CLIENT_SECRET=... \
HTTPS_PROXY=http://127.0.0.1:6789 HTTP_PROXY=http://127.0.0.1:6789 \
NO_PROXY=localhost,127.0.0.1,::1 \
./server

# gateway（application.yml 的 ${JWT_SECRET} 需非空占位；不本地验签）
cd services/gateway && JWT_SECRET=any-non-empty mvn spring-boot:run

# web
cd web && npm run dev
```

验证 OAuth 链路：`curl -i http://localhost:8080/api/v1/auth/oauth/google` 应 `302 → accounts.google.com`。
看日志：`tail -f /tmp/auth-server.log`（关注 `oauth callback ... exchange OK / auto-linked / intermediate issued` 与 `oauth exchange: LOGIN SUCCESS`）。
