# Google / 手机号 登录 接入与对接方案

auth-service 支持两种免密登录：**Google OAuth** 与**手机号短信验证码**。两者均经网关（`/api/v1/auth/**` 公开、`/api/v1/account/**` 鉴权）。微信登录已移除。

---

## 一、Google 登录 —— 完整对接方案

### 1. 架构（BFF「后端换码 + 一次性中间码」模式）

```
前端 ──「Google 登录」──> GET  /api/v1/auth/oauth/google        (网关公开)
   auth-service: 生成 state(Redis) + oauth_state cookie, 302 → Google 授权页
Google 授权后 302 ──> GET /api/v1/auth/callback/google?code&state  (网关公开)
   auth-service: 校验 state+cookie → code 换 access_token → 拉 userinfo(sub/email/name/picture)
                → 按 (provider=google, provider_user_id=sub) find-or-create 用户与 oauth_accounts 绑定
                → 签发一次性中间码(Redis 30s) → 302 回前端 /auth/callback/google?code=<中间码>
前端 回调页 ──> POST /api/v1/auth/oauth/exchange {中间码}        (网关公开)
   → { user, tokens }（与密码登录同形状）→ setToken → 跳首页
```

> token 永不进 URL，也不会出现在浏览器历史/日志；中间码一次性、30s 失效。

### 2. Google Cloud Console 配置（一次性）

1. **创建项目**（或用现有项目）→ 左上确认选对项目。
2. **OAuth 同意屏幕（OAuth consent screen）**：
   - User type：**External**（本地/小规模够用）；状态会停在 **Testing**。
   - App name：`WenxinBlog`；User support email / Developer contact：你的 Gmail。
   - Authorized domains：**留空**（localhost 不是可注册域名）。
   - **Scopes**（关键）：`Add or Remove Scopes` 勾选 `userinfo.email`、`userinfo.profile`、`openid` —— 对应后端 `scope=openid email profile`，少勾拿不到邮箱/昵称。
   - **Test users**（关键）：`+ Add Users` 填你自己登录用的 Gmail。Testing 状态下只有这里的人能授权，否则报 `access_denied`。
3. **凭据（Credentials）→ 创建 OAuth 客户端 ID**：
   - 应用类型：**Web application**。
   - **已获授权的重定向 URI**：`http://localhost:8080/api/v1/auth/callback/google`（须与 `GOOGLE_REDIRECT_URL` 一字不差）。
   - 已获授权的 JavaScript 来源：`http://localhost:3000`（当前服务端流程不校验，填了无妨）。
4. 创建后弹窗给 **Client ID** + **Client Secret**，填进 `.env`（见第 3 步）。
   > 官方提示：配置生效可能需要 5 分钟～几小时；刚建好立即测若失败，等几分钟再试。

### 3. 项目配置（`.env`，docker-compose 自动透传）

```bash
GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-xxxx
GOOGLE_REDIRECT_URL=http://localhost:8080/api/v1/auth/callback/google
```

留空则 `GOOGLE_CLIENT_ID` 为空 → auth-service 启动时**不启用** google provider（`/api/v1/auth/oauth/google` 返回 400）。

### 4. 端到端测试步骤

```bash
cd /Users/yuwenxin/AIProjects/wenxinBlog
docker compose up -d --build auth-service gateway web   # 至少起这三个 + redis + postgres-auth
docker compose logs auth-service | grep "oauth provider enabled"
#   期望看到：oauth provider enabled: google
```

然后：

1. 浏览器开 `http://localhost:3000/login`。
2. 点「Google 登录」→ 跳到 Google 授权页。
3. 首次会显示「Google 未验证此应用」→ 点 **高级** → **转至 WenxinBlog（不安全）**（你自己的测试应用，正常）。
4. 同意授权 → 自动跳回 `/auth/callback/google` → 处理 → 跳首页，处于已登录。
5. **幂等性验证**：退出后再次「Google 登录」→ 应登录到**同一个**账号（不会重复创建）。

数据库核验（可选）：

```sql
-- auth_db
SELECT u.id, u.username, u.email, o.provider, o.provider_user_id
FROM users u JOIN oauth_accounts o ON o.user_id = u.id;
```

### 5. 常见报错与排查

| 现象                                              | 原因                                                     | 处理                                                                                |
| ------------------------------------------------- | -------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| `redirect_uri_mismatch`                           | 控制台 redirect URI 与 `GOOGLE_REDIRECT_URL` 不一致      | 二者改成完全一致（含 http/https、端口、路径）                                       |
| `access_denied`                                   | 同意屏幕 Testing 且未把你的 Gmail 加进 Test users        | OAuth consent screen → Test users 加上                                              |
| `invalid_or_expired_state` / 回调 `invalid_state` | state 失效（>10min）或 cookie 被拦                       | 重新点登录；检查浏览器是否拦了 `oauth_state` cookie（同源 localhost 应放行）        |
| 登录后 email 为空                                 | userinfo `email_verified=false` → 后端故意丢弃未验证邮箱 | 该 Google 账号没验证邮箱；用已验证邮箱的账号，或接受空邮箱（仍可登录，按 sub 建号） |
| `unsupported_provider`                            | `GOOGLE_CLIENT_ID` 没传进容器                            | 检查 `.env` + `docker compose up -d --build auth-service`；看启动日志               |
| 回调一直转圈                                      | 前端回调页 `/auth/callback/[provider]` 路由没生效        | 确认 web 已重新构建（`docker compose up -d --build web`）                           |

### 6. 账号绑定（已登录用户关联 Google）

手动绑定策略：Google 登录**默认创建独立新账号**，绝不按邮箱自动合并。已登录用户在「设置」页主动绑定：

- 入口：`http://localhost:3000/settings` →「第三方账号」→「绑定」。
- 流程：`POST /api/v1/account/oauth/google/link`（带 Bearer，网关注入 `X-User-Id`）→ 返回授权 URL → 同样的回调 → 这次 `state` 意图为 `link:<userId>` → 绑定到当前用户（不新建）。
- 解绑：同一卡片「解绑」→ `DELETE /api/v1/account/oauth/google`。
- 若该 Google 身份已绑到**别人**，绑定回调返回 `409 已绑定到其他用户`。

### 7. 后端代码位置（实现参考）

| 关注点                                       | 文件                                               |
| -------------------------------------------- | -------------------------------------------------- |
| Google provider（AuthURL/Exchange/userinfo） | `internal/service/oauth_provider.go`               |
| find-or-create / link / 中间码兑换           | `internal/service/oauth_service.go`                |
| oauth_accounts 读写                          | `internal/repository/oauth_repository.go`          |
| 路由（公开 + `/account` 鉴权）               | `internal/handler/oauth_handler.go`                |
| state/中间码/cookie                          | `internal/cache/redis.go` + handler                |
| 启用 provider                                | `cmd/server/main.go`（按 `GOOGLE_CLIENT_ID` 启用） |

### 8. 生产上线注意

- **HTTPS + 独立域名**：生产环境前端（如 `https://wenxinblog.com`）与网关需同注册域，否则 `oauth_state` cookie 跨域发不出；必要时用 `SameSite=None; Secure`（目前是 `SameSite=Lax`，仅适合同源/localhost）。Google 控制台再**追加**一条 `https://wenxinblog.com/api/v1/auth/callback/google`，并把 `.env` 的 `GOOGLE_REDIRECT_URL` 切到生产值。
- **同意屏幕从 Testing → In production**：发布前在 OAuth consent screen 提交验证（Google 审核），否则只有 Test users 能用。
- **Client Secret 保护**：`.env` 已 gitignore；不要进前端环境变量（`NEXT_PUBLIC_*` 会泄露到浏览器）。
- **Token 存储安全（既有遗留）**：前端目前把 access token 存 localStorage + 非 httpOnly cookie（有 XSS 暴露面，项目原有 TODO）。本方案未改变该模型；后续可改为「后端 set httpOnly cookie + `/auth/refresh` 静默续期」。

---

## 二、手机号 短信验证码登录

> ⚠️ 阿里云官方示例常给的是 `dypnsapi`（号码认证 / 一键登录），**那是另一个产品**。本系统用的是**短信服务 `dysmsapi` 的 `SendSms`** 发验证码，对应「手机号 + 验证码」流程。

- 流程：`POST /api/v1/auth/phone/send-code {phone}`（限流：号码 1/min·5/day、IP 10/h）→ `POST /api/v1/auth/phone/login {phone, code}` → 校验 + find-or-create + 签发 token。
- 实现：`internal/service/sms_sender.go`（`MockSmsSender` 本地打印 / `AliyunSmsSender` **手写 HMAC-SHA1 签名调用 dysmsapi**，不引 SDK，贴合离线/最小依赖）。
- 本地联调：`.env` 设 `SMS_PROVIDER=mock`，验证码打印在 auth-service 日志，无需真实凭据即可跑通。
- 上线接真实短信：
  1. 阿里云短信服务开通 → 申请**签名** + **验证码模板**（模板变量须为 `${code}`）。
  2. 建 RAM 用户（授予 `AliyunDysmsFullAccess`）取 AccessKey。
  3. `.env` 设 `SMS_PROVIDER=aliyun` 并填 `ALIYUN_SMS_ACCESS_KEY_ID/_SECRET/SIGN_NAME/TEMPLATE_CODE`。
  4. **上线前务必用真实凭据端到端发一条验证**（手写签名易因编码细节出错）。

---

## 三、数据库迁移

- **auth_db（自动）**：启动时自动执行 `db/migrations/000004_oauth_and_phone.up.sql`
  （放宽 email/password_hash 可空 + 部分唯一索引、新增 phone 列、新增 oauth_accounts 表）。
- **user_db（手工，无迁移器）**：对 `user_db` 执行一次
  `services/user-service/db/manual_migration_nullable_email.sql`（email/password_hash 改可空 + 部分唯一索引，否则多个无邮箱社交用户同步会撞 UNIQUE）。

---

## 四、环境变量速查（`.env`）

```bash
# Google
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_REDIRECT_URL=http://localhost:8080/api/v1/auth/callback/google
# 短信
SMS_PROVIDER=mock                # mock | aliyun
ALIYUN_SMS_ACCESS_KEY_ID=
ALIYUN_SMS_ACCESS_KEY_SECRET=
ALIYUN_SMS_SIGN_NAME=
ALIYUN_SMS_TEMPLATE_CODE=
# OAuth 回换取 token 后 302 回前端的目标
FRONTEND_URL=http://localhost:3000
```
