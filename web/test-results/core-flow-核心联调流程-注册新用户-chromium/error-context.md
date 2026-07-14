# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: core-flow.spec.ts >> 核心联调流程 >> 注册新用户
- Location: e2e/core-flow.spec.ts:22:7

# Error details

```
TimeoutError: page.waitForURL: Timeout 15000ms exceeded.
=========================== logs ===========================
waiting for navigation to "/" until "load"
============================================================
```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
    - generic [ref=e4]:
        - generic [ref=e5]:
            - link "W WenxinBlog" [ref=e6] [cursor=pointer]:
                - /url: /
                - generic [ref=e8]: W
                - generic [ref=e9]: WenxinBlog
            - paragraph [ref=e10]: // create account
        - generic [ref=e11]:
            - generic [ref=e12]:
                - generic [ref=e14]: 用户名*
                - textbox "用户名*" [ref=e17]:
                    - /placeholder: 请输入用户名
                    - text: e2e_1783993485798
            - generic [ref=e18]:
                - generic [ref=e20]: 昵称
                - textbox "昵称" [ref=e23]:
                    - /placeholder: 请输入昵称（可选）
            - generic [ref=e24]:
                - generic [ref=e26]: 邮箱*
                - textbox "邮箱*" [ref=e29]:
                    - /placeholder: 请输入邮箱
                    - text: e2e_1783993485798@test.com
            - generic [ref=e30]:
                - generic [ref=e32]: 密码*
                - generic [ref=e34]:
                    - textbox "密码*" [ref=e35]:
                        - /placeholder: 请输入密码
                        - text: password123
                    - button "Show password" [ref=e36]:
                        - img "eye_closed_solid" [ref=e37]:
                            - img [ref=e38]
            - generic [ref=e41]:
                - generic [ref=e43]: 确认密码*
                - generic [ref=e45]:
                    - textbox "确认密码*" [ref=e46]:
                        - /placeholder: 请再次输入密码
                        - text: password123
                    - button "Show password" [ref=e47]:
                        - img "eye_closed_solid" [ref=e48]:
                            - img [ref=e49]
            - button "注册" [ref=e52] [cursor=pointer]:
                - generic [ref=e53]: 注册
        - generic [ref=e54]:
            - text: 已有账号？
            - link "立即登录" [ref=e55] [cursor=pointer]:
                - /url: /login
    - alert [ref=e56]
```

# Test source

```ts
  1   | import { test, expect } from '@playwright/test';
  2   |
  3   | const TEST_USER = { email: 'dave2@wenxinblog.com', password: 'password123' };
  4   |
  5   | test.describe('核心联调流程', () => {
  6   |
  7   |   test('首页加载并显示真实博文', async ({ page }) => {
  8   |     await page.goto('/');
  9   |     // 等待帖子加载
  10  |     await page.waitForTimeout(5000);
  11  |     // 应该有博文卡片（不是 EmptyState）
  12  |     const postCards = page.locator('article');
  13  |     await expect(postCards.first()).toBeVisible({ timeout: 10000 });
  14  |     const count = await postCards.count();
  15  |     expect(count).toBeGreaterThan(0);
  16  |     // 不应包含"演示数据"文本
  17  |     const body = await page.textContent('body');
  18  |     expect(body).not.toContain('演示数据');
  19  |     expect(body).not.toContain('MOCK');
  20  |   });
  21  |
  22  |   test('注册新用户', async ({ page }) => {
  23  |     const unique = `e2e_${Date.now()}`;
  24  |     await page.goto('/register');
  25  |     await page.waitForSelector('#username', { timeout: 10000 });
  26  |     await page.fill('#username', unique);
  27  |     await page.fill('#email', `${unique}@test.com`);
  28  |     await page.fill('#password', 'password123');
  29  |     await page.fill('#confirmPassword', 'password123');
  30  |     await page.click('button[type="submit"]');
> 31  |     await page.waitForURL('/', { timeout: 15000 });
      |                ^ TimeoutError: page.waitForURL: Timeout 15000ms exceeded.
  32  |   });
  33  |
  34  |   test('登录 → 首页显示登录态', async ({ page }) => {
  35  |     await page.goto('/login');
  36  |     await page.waitForSelector('#email', { timeout: 10000 });
  37  |     await page.fill('#email', TEST_USER.email);
  38  |     await page.fill('#password', TEST_USER.password);
  39  |     await page.click('button[type="submit"]');
  40  |     await page.waitForURL('/', { timeout: 15000 });
  41  |     await expect(page.locator('text=写博文')).toBeVisible({ timeout: 10000 });
  42  |   });
  43  |
  44  |   test('登录 → 发帖 → 详情页', async ({ page }) => {
  45  |     // 登录
  46  |     await page.goto('/login');
  47  |     await page.waitForSelector('#email', { timeout: 10000 });
  48  |     await page.fill('#email', TEST_USER.email);
  49  |     await page.fill('#password', TEST_USER.password);
  50  |     await page.click('button[type="submit"]');
  51  |     await page.waitForURL('/', { timeout: 15000 });
  52  |
  53  |     // 进编辑器
  54  |     await page.click('a[href="/editor"]');
  55  |     await page.waitForURL('/editor', { timeout: 10000 });
  56  |
  57  |     // 填写博文
  58  |     const title = `E2E测试帖_${Date.now()}`;
  59  |     await page.waitForSelector('input[class*="text-3xl"]', { timeout: 5000 });
  60  |     await page.fill('input[class*="text-3xl"]', title);
  61  |     const textarea = page.locator('textarea').first();
  62  |     await textarea.fill('## E2E 自动化测试\n这是一个由 Playwright 自动创建的测试博文。');
  63  |
  64  |     // 发布
  65  |     await page.click('button:has-text("发布")');
  66  |     await page.waitForURL(/\/posts\//, { timeout: 20000 });
  67  |
  68  |     // 验证详情页内容
  69  |     await expect(page.locator('h1')).toContainText(title, { timeout: 15000 });
  70  |     // 作者名不应是纯 UUID
  71  |     const body = await page.textContent('body');
  72  |     expect(body).not.toMatch(/author.*[0-9a-f]{8}-[0-9a-f]{4}/);
  73  |   });
  74  |
  75  |   test('详情页 → 评论', async ({ page }) => {
  76  |     // 登录
  77  |     await page.goto('/login');
  78  |     await page.waitForSelector('#email', { timeout: 10000 });
  79  |     await page.fill('#email', TEST_USER.email);
  80  |     await page.fill('#password', TEST_USER.password);
  81  |     await page.click('button[type="submit"]');
  82  |     await page.waitForURL('/', { timeout: 15000 });
  83  |
  84  |     // 点击第一个帖子
  85  |     await page.waitForTimeout(5000);
  86  |     const firstPost = page.locator('article a[href^="/posts/"]').first();
  87  |     await firstPost.click();
  88  |     await page.waitForURL(/\/posts\//, { timeout: 10000 });
  89  |     await page.waitForTimeout(5000);
  90  |
  91  |     // 发表评论
  92  |     const commentInput = page.locator('input[placeholder*="评论"]').first();
  93  |     if (await commentInput.isVisible({ timeout: 5000 }).catch(() => false)) {
  94  |       await commentInput.fill('E2E 自动评论');
  95  |       await page.click('button:has-text("发送")');
  96  |       await page.waitForTimeout(3000);
  97  |       const body = await page.textContent('body');
  98  |       expect(body).toContain('E2E 自动评论');
  99  |     }
  100 |   });
  101 |
  102 |   test('刷新页面保持登录态', async ({ page }) => {
  103 |     // 登录
  104 |     await page.goto('/login');
  105 |     await page.waitForSelector('#email', { timeout: 10000 });
  106 |     await page.fill('#email', TEST_USER.email);
  107 |     await page.fill('#password', TEST_USER.password);
  108 |     await page.click('button[type="submit"]');
  109 |     await page.waitForURL('/', { timeout: 15000 });
  110 |
  111 |     // 刷新
  112 |     await page.reload();
  113 |     await page.waitForTimeout(5000);
  114 |     await expect(page.locator('text=写博文')).toBeVisible({ timeout: 10000 });
  115 |   });
  116 | });
  117 |
```
