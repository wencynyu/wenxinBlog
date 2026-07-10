# 安全架构设计

## 安全体系

```
┌─────────────────────────────────────────────────────────────┐
│                        安全层次                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐ │
│  │   网络安全      │  │   应用安全      │  │   数据安全      │ │
│  │                │  │                │  │                │ │
│  │ - VPC隔离      │  │ - 认证鉴权     │  │ - 加密存储      │ │
│  │ - 安全组       │  │ - 权限控制     │  │ - 备份恢复      │ │
│  │ - WAF防火墙    │  │ - SQL注入防护  │  │ - 敏感数据脱敏  │ │
│  │ - DDoS防护     │  │ - XSS防护      │  │ - 日志审计      │ │
│  └────────────────┘  └────────────────┘  └────────────────┘ │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

## 认证授权

### 认证流程

```
┌─────────────────────────────────────────────────────────────┐
│                        认证流程                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. 用户注册                                                 │
│     ┌─────────┐                                             │
│     │ 前端    │ -> POST /api/v1/auth/register               │
│     └────┬────┘    { username, email, password }           │
│          │                                                   │
│          v                                                   │
│     ┌─────────┐                                             │
│     │Gateway  │ -> 验证参数                                │
│     └────┬────┘                                             │
│          │                                                   │
│          v                                                   │
│     ┌─────────────┐                                        │
│     │auth-service │ -> 创建用户                             │
│     │             │    - 密码哈希 (bcrypt)                  │
│     │             │    - 发送验证邮件                        │
│     └─────────────┘                                        │
│                                                              │
│  2. 用户登录                                                 │
│     ┌─────────┐                                             │
│     │ 前端    │ -> POST /api/v1/auth/login                  │
│     └────┬────┘    { email, password }                      │
│          │                                                   │
│          v                                                   │
│     ┌─────────────┐                                        │
│     │auth-service │ -> 验证密码                             │
│     │             │    - 生成JWT Token                       │
│     │             │    - 存储Session (Redis)                 │
│     └──────┬──────┘                                        │
│            │                                                │
│            v                                                │
│     { token, user }                                         │
│            │                                                │
│            v                                                │
│     ┌─────────┐     存储 Token                               │
│     │ 前端    │ -> LocalStorage                             │
│     └─────────┘     后续请求携带: Authorization: Bearer xxx  │
│                                                              │
│  3. Token验证                                                │
│     后续请求 -> Gateway -> 验证JWT -> 透传用户信息            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### JWT设计

```
JWT Payload:
{
  "iss": "wenxinblog",           // 签发者
  "sub": "user-id",               // 用户ID
  "iat": 1234567890,              // 签发时间
  "exp": 1234654290,              // 过期时间 (24h)
  "type": "access",               // Token类型
  "roles": ["user"],              // 用户角色
  "permissions": [                // 权限列表
    "post:read",
    "post:write"
  ]
}

Token刷新:
- Access Token: 24小时有效
- Refresh Token: 30天有效
- 刷新接口: POST /api/v1/auth/refresh
```

### OAuth2.0

```
支持的OAuth提供商:
- Google
- GitHub
- 微信

流程:
1. 前端: 重定向到OAuth授权页面
2. 用户: 同意授权
3. OAuth: 回调 /api/v1/auth/callback/{provider}
4. auth-service: 换取用户信息
5. auth-service: 创建/绑定账号
6. auth-service: 签发JWT
```

## 权限控制

### RBAC模型

```
┌─────────────────────────────────────────────────────────────┐
│                        RBAC模型                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  用户 (User)                                                 │
│    │                                                         │
│    ├── 角色 (Role)                                          │
│    │    │                                                    │
│    │    ├── 普通用户 (USER)                                 │
│    │    │    ├── post:read (读博文)                         │
│    │    │    ├── post:write (写博文)                        │
│    │    │    └── comment:write (写评论)                     │
│    │    │                                                    │
│    │    ├── VIP用户 (VIP)                                   │
│    │    │    ├── 继承 USER 权限                             │
│    │    │    ├── post:pin (置顶)                            │
│    │    │    └── content:upload (上传更多)                  │
│    │    │                                                    │
│    │    ├── 管理员 (ADMIN)                                  │
│    │    │    ├── post:delete (删除博文)                     │
│    │    │    ├── user:manage (管理用户)                     │
│    │    │    └── ad:manage (管理广告)                       │
│    │    │                                                    │
│    │    └── 超级管理员 (SUPER_ADMIN)                        │
│    │         ├── 所有权限                                    │
│    │         └── system:config (系统配置)                   │
│    │                                                         │
│    └── 权限 (Permission)                                    │
│         - 资源:操作 (resource:action)                       │
│         - 细粒度控制                                         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 权限验证

```java
// Gateway权限过滤器
@Component
public class AuthorizationFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String userId = extractUserId(exchange);

        // 检查用户权限
        List<String> permissions = getPermissions(userId);
        String requiredPermission = getRequiredPermission(path);

        if (!permissions.contains(requiredPermission)) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }
}
```

### 数据权限

```
场景: 用户只能管理自己的数据

实现方式:
1. 应用层检查
   if (post.getAuthorId().equals(currentUserId)) {
       // 允许操作
   }

2. 数据库层检查 (Row Level Security)
   CREATE POLICY user_posts_policy ON posts
   FOR ALL
   TO app_user
   USING (author_id = current_user_id());
```

## 数据安全

### 加密存储

```
敏感数据加密:
- 密码: bcrypt (不可逆)
- JWT Secret: AES-256
- API密钥: AES-256
- OAuth Secret: AES-256

存储:
- 使用KMS (密钥管理服务)
- 定期轮换密钥
- 硬件安全模块 (HSM)
```

### 传输加密

```
HTTPS:
- 强制使用HTTPS
- TLS 1.3
- HSTS (HTTP Strict Transport Security)

内网通信:
- Service Mesh (mTLS)
- API网关到服务: HTTPS
- 服务间: 可选mTLS
```

### 数据脱敏

```
日志脱敏:
- 密码: *******
- 手机号: 138****5678
- 身份证: 3301**********1234

数据库脱敏:
- 部分字段加密存储
- 查询时解密
- 非必要不展示
```

## 防护措施

### SQL注入防护

```
防护方式:
1. 使用参数化查询 (预编译)
2. ORM框架 (JPA, MyBatis)
3. 输入验证

// 不安全 (SQL注入风险)
String sql = "SELECT * FROM users WHERE id = " + userId;

// 安全 (参数化)
String sql = "SELECT * FROM users WHERE id = ?";
PreparedStatement stmt = connection.prepareStatement(sql);
stmt.setString(1, userId);
```

### XSS防护

```
防护方式:
1. 输入过滤: 移除危险标签
2. 输出转义: HTML实体转义
3. CSP (Content Security Policy)

// DOMPurify (前端)
import DOMPurify from 'dompurify';
const clean = DOMPurify.sanitize(dirtyInput);

// 后端
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
String clean = Jsoup.clean(input, Whitelist.basic());
```

### CSRF防护

```
防护方式:
1. CSRF Token
2. SameSite Cookie属性
3. 验证Referer

// Spring Security
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }
}
```

### DDoS防护

```
防护层次:
1. 网络层
   - 阿里云DDoS防护
   - IP黑名单
   - 流量清洗

2. 应用层
   - 限流 (Token Bucket)
   - IP限流
   - CAPTCHA

3. CDN层
   - CDN吸收流量
   - 边缘节点防护
```

## 安全审计

### 日志审计

```
审计日志内容:
- 用户ID
- 操作类型
- 操作资源
- IP地址
- User-Agent
- 时间戳
- 结果 (成功/失败)

存储:
- 独立的审计日志
- 不可修改
- 长期保存 (6个月+)
```

### 异常检测

```
异常行为:
1. 登录异常
   - 短时间内多次失败
   - 异地登录
   - 异常时间登录

2. 操作异常
   - 异常操作频率
   - 越权操作尝试
   - 批量操作

3. 流量异常
   - 突然流量激增
   - 异常User-Agent
   - 单IP大量请求
```

## 合规要求

### 个人信息保护

```
遵循法规:
- 《个人信息保护法》
- 《网络安全法》
- 《数据安全法》

措施:
1. 最小化收集
2. 明确告知用途
3. 获得用户同意
4. 提供删除渠道
5. 数据匿名化
```

### 等级保护

```
等保三级要求:
1. 身份鉴别
   - 双因素认证
   - 密码复杂度

2. 访问控制
   - 最小权限原则
   - 权限审计

3. 安全审计
   - 日志留存6个月
   - 审计记录

4. 数据备份
   - 每日备份
   - 异地容灾

5. 渗透测试
   - 每年至少一次
   - 修复漏洞
```

## 安全最佳实践

1. **默认拒绝**
   - 白名单而非黑名单
   - 默认拒绝访问

2. **最小权限**
   - 只授予必要的权限
   - 定期审查权限

3. **纵深防御**
   - 多层防护
   - 不要依赖单一防线

4. **安全开发生命周期**
   - 需求阶段: 安全需求分析
   - 开发阶段: 安全编码规范
   - 测试阶段: 安全测试
   - 运维阶段: 安全监控

5. **定期演练**
   - 应急响应演练
   - 渗透测试
   - 红蓝对抗
```
