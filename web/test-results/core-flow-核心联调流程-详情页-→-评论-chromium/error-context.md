# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: core-flow.spec.ts >> 核心联调流程 >> 详情页 → 评论
- Location: e2e/core-flow.spec.ts:75:7

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: locator.click: Test timeout of 30000ms exceeded.
Call log:
  - waiting for locator('article a[href^="/posts/"]').first()

```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
    - alert [ref=e2]
    - generic [ref=e3]:
        - banner [ref=e4]:
            - generic [ref=e6]:
                - link "W WenxinBlog" [ref=e7] [cursor=pointer]:
                    - /url: /
                    - generic [ref=e9]: W
                    - generic [ref=e10]: WenxinBlog
                - generic [ref=e12]:
                    - img "search" [ref=e14]:
                        - img [ref=e15]
                    - textbox "搜索博文..." [ref=e17]
                - generic [ref=e18]:
                    - button "moon" [ref=e19] [cursor=pointer]:
                        - img "moon" [ref=e21]:
                            - img [ref=e22]
                    - button "bell" [ref=e24] [cursor=pointer]:
                        - img "bell" [ref=e26]:
                            - img [ref=e27]
                    - button "dave2 dave2" [ref=e30]:
                        - listitem [ref=e31]:
                            - img "dave2" [ref=e33]: d
                        - generic [ref=e34]: dave2
        - navigation [ref=e35]:
            - menu [ref=e41]:
                - menuitem "home 首页" [ref=e42] [cursor=pointer]:
                    - link "home 首页" [ref=e44]:
                        - /url: /
                        - img "home" [ref=e45]:
                            - img [ref=e46]
                        - generic [ref=e48]: 首页
                - menuitem "star_stroked 推荐" [ref=e49] [cursor=pointer]:
                    - link "star_stroked 推荐" [ref=e51]:
                        - /url: /feed
                        - img "star_stroked" [ref=e52]:
                            - img [ref=e53]
                        - generic [ref=e55]: 推荐
                - menuitem "like_heart 热门" [ref=e56] [cursor=pointer]:
                    - link "like_heart 热门" [ref=e58]:
                        - /url: /trending
                        - img "like_heart" [ref=e59]:
                            - img [ref=e60]
                        - generic [ref=e62]: 热门
                - menuitem "book 博文" [ref=e63] [cursor=pointer]:
                    - link "book 博文" [ref=e65]:
                        - /url: /posts
                        - img "book" [ref=e66]:
                            - img [ref=e67]
                        - generic [ref=e69]: 博文
        - main [ref=e70]:
            - generic [ref=e71]:
                - generic [ref=e72]:
                    - generic [ref=e73]:
                        - paragraph [ref=e74]: // wenxinblog · engineering folio
                        - heading "发现值得读的技术文章" [level=1] [ref=e75]
                        - paragraph [ref=e76]: 基于你的兴趣与阅读行为，精选高质量工程实践内容。
                        - link "plus 写博文" [ref=e77] [cursor=pointer]:
                            - /url: /editor
                            - button "plus 写博文" [ref=e78]:
                                - generic [ref=e79]:
                                    - img "plus" [ref=e80]:
                                        - img [ref=e81]
                                    - generic [ref=e83]: 写博文
                    - paragraph [ref=e84]: // latest posts
                    - generic [ref=e86]:
                        - link [ref=e87] [cursor=pointer]:
                            - /url: /posts/e3c973ae-5a7c-45b5-a8df-7cfe0b3615ed
                            - article [ref=e88]:
                                - generic [ref=e89]:
                                    - generic [ref=e90]:
                                        - listitem [ref=e91]:
                                            - img "dave2" [ref=e93]: d
                                        - generic [ref=e94]:
                                            - generic [ref=e95]: dave2
                                            - generic [ref=e96]: 几秒前
                                    - heading "E2E测试帖_1783993507762" [level=3] [ref=e97]
                                    - generic [ref=e98]:
                                        - button "like_heart 0" [ref=e99]:
                                            - img "like_heart" [ref=e100]:
                                                - img [ref=e101]
                                            - generic [ref=e103]: '0'
                                        - button "star_stroked 收藏" [ref=e104]:
                                            - img "star_stroked" [ref=e105]:
                                                - img [ref=e106]
                                            - generic [ref=e108]: 收藏
                                        - generic [ref=e109]:
                                            - img "comment" [ref=e110]:
                                                - img [ref=e111]
                                            - generic [ref=e113]: '0'
                        - link [ref=e114] [cursor=pointer]:
                            - /url: /posts/dd1138db-d52c-4dbe-a465-2543dd1083a6
                            - article [ref=e115]:
                                - generic [ref=e116]:
                                    - generic [ref=e117]:
                                        - listitem [ref=e118]:
                                            - img "dave2" [ref=e120]: d
                                        - generic [ref=e121]:
                                            - generic [ref=e122]: dave2
                                            - generic [ref=e123]: 1 天前
                                    - heading "联调验证帖" [level=3] [ref=e124]
                                    - generic [ref=e125]:
                                        - button "like_heart 0" [ref=e126]:
                                            - img "like_heart" [ref=e127]:
                                                - img [ref=e128]
                                            - generic [ref=e130]: '0'
                                        - button "star_stroked 收藏" [ref=e131]:
                                            - img "star_stroked" [ref=e132]:
                                                - img [ref=e133]
                                            - generic [ref=e135]: 收藏
                                        - generic [ref=e136]:
                                            - img "comment" [ref=e137]:
                                                - img [ref=e138]
                                            - generic [ref=e140]: '0'
                        - link [ref=e141] [cursor=pointer]:
                            - /url: /posts/48bc50a5-c160-4ea1-aa61-579d8e7acc19
                            - article [ref=e142]:
                                - generic [ref=e143]:
                                    - generic [ref=e144]:
                                        - listitem [ref=e145]:
                                            - img "测试用户" [ref=e147]: 测
                                        - generic [ref=e148]:
                                            - generic [ref=e149]: 测试用户
                                            - generic [ref=e150]: 1 天前
                                    - heading "测试标题" [level=3] [ref=e151]
                                    - generic [ref=e152]:
                                        - button "like_heart 1" [ref=e153]:
                                            - img "like_heart" [ref=e154]:
                                                - img [ref=e155]
                                            - generic [ref=e157]: '1'
                                        - button "star_stroked 收藏" [ref=e158]:
                                            - img "star_stroked" [ref=e159]:
                                                - img [ref=e160]
                                            - generic [ref=e162]: 收藏
                                        - generic [ref=e163]:
                                            - img "comment" [ref=e164]:
                                                - img [ref=e165]
                                            - generic [ref=e167]: '1'
                        - link [ref=e168] [cursor=pointer]:
                            - /url: /posts/a32a6400-f5ce-4b3d-af39-525b30c42fe6
                            - article [ref=e169]:
                                - generic [ref=e170]:
                                    - generic [ref=e171]:
                                        - listitem [ref=e172]:
                                            - img "dave2" [ref=e174]: d
                                        - generic [ref=e175]:
                                            - generic [ref=e176]: dave2
                                            - generic [ref=e177]: 3 天前
                                    - heading "前后端联调第一篇" [level=3] [ref=e178]
                                    - paragraph [ref=e179]: 联调测试
                                    - generic [ref=e180]:
                                        - button "like_heart 0" [ref=e181]:
                                            - img "like_heart" [ref=e182]:
                                                - img [ref=e183]
                                            - generic [ref=e185]: '0'
                                        - button "star_stroked 收藏" [ref=e186]:
                                            - img "star_stroked" [ref=e187]:
                                                - img [ref=e188]
                                            - generic [ref=e190]: 收藏
                                        - generic [ref=e191]:
                                            - img "comment" [ref=e192]:
                                                - img [ref=e193]
                                            - generic [ref=e195]: '0'
                        - link [ref=e196] [cursor=pointer]:
                            - /url: /posts/27c5eec5-44b5-42be-b050-6e55d814e268
                            - article [ref=e197]:
                                - generic [ref=e198]:
                                    - generic [ref=e199]:
                                        - listitem [ref=e200]:
                                            - img "dave2" [ref=e202]: d
                                        - generic [ref=e203]:
                                            - generic [ref=e204]: dave2
                                            - generic [ref=e205]: 2 天前
                                    - heading "Bug fix 后发帖" [level=3] [ref=e206]
                                    - generic [ref=e207]:
                                        - button "like_heart 0" [ref=e208]:
                                            - img "like_heart" [ref=e209]:
                                                - img [ref=e210]
                                            - generic [ref=e212]: '0'
                                        - button "star_stroked 收藏" [ref=e213]:
                                            - img "star_stroked" [ref=e214]:
                                                - img [ref=e215]
                                            - generic [ref=e217]: 收藏
                                        - generic [ref=e218]:
                                            - img "comment" [ref=e219]:
                                                - img [ref=e220]
                                            - generic [ref=e222]: '0'
                        - link [ref=e223] [cursor=pointer]:
                            - /url: /posts/d6c2ed87-5809-4929-9046-046bb5972b1e
                            - article [ref=e224]:
                                - generic [ref=e225]:
                                    - generic [ref=e226]:
                                        - listitem [ref=e227]:
                                            - img "dave2" [ref=e229]: d
                                        - generic [ref=e230]:
                                            - generic [ref=e231]: dave2
                                            - generic [ref=e232]: 2 天前
                                    - heading "AuthFilter 成功" [level=3] [ref=e233]
                                    - generic [ref=e234]:
                                        - button "like_heart 0" [ref=e235]:
                                            - img "like_heart" [ref=e236]:
                                                - img [ref=e237]
                                            - generic [ref=e239]: '0'
                                        - button "star_stroked 收藏" [ref=e240]:
                                            - img "star_stroked" [ref=e241]:
                                                - img [ref=e242]
                                            - generic [ref=e244]: 收藏
                                        - generic [ref=e245]:
                                            - img "comment" [ref=e246]:
                                                - img [ref=e247]
                                            - generic [ref=e249]: '0'
                - complementary [ref=e252]:
                    - generic [ref=e253]:
                        - heading "star_stroked // popular posts" [level=5] [ref=e254]:
                            - img "star_stroked" [ref=e255]:
                                - img [ref=e256]
                            - text: // popular posts
                        - generic [ref=e258]:
                            - link "1 E2E测试帖_1783993507762 0 赞 · 0 评论" [ref=e259] [cursor=pointer]:
                                - /url: /posts/e3c973ae-5a7c-45b5-a8df-7cfe0b3615ed
                                - generic [ref=e260]:
                                    - generic [ref=e261]: '1'
                                    - generic [ref=e262]:
                                        - generic [ref=e263]: E2E测试帖_1783993507762
                                        - generic [ref=e264]: 0 赞 · 0 评论
                            - link "2 测试标题 1 赞 · 1 评论" [ref=e265] [cursor=pointer]:
                                - /url: /posts/48bc50a5-c160-4ea1-aa61-579d8e7acc19
                                - generic [ref=e266]:
                                    - generic [ref=e267]: '2'
                                    - generic [ref=e268]:
                                        - generic [ref=e269]: 测试标题
                                        - generic [ref=e270]: 1 赞 · 1 评论
                            - link "3 Bug fix 后发帖 0 赞 · 0 评论" [ref=e271] [cursor=pointer]:
                                - /url: /posts/27c5eec5-44b5-42be-b050-6e55d814e268
                                - generic [ref=e272]:
                                    - generic [ref=e273]: '3'
                                    - generic [ref=e274]:
                                        - generic [ref=e275]: Bug fix 后发帖
                                        - generic [ref=e276]: 0 赞 · 0 评论
                            - link "4 联调验证帖 0 赞 · 0 评论" [ref=e277] [cursor=pointer]:
                                - /url: /posts/dd1138db-d52c-4dbe-a465-2543dd1083a6
                                - generic [ref=e278]:
                                    - generic [ref=e279]: '4'
                                    - generic [ref=e280]:
                                        - generic [ref=e281]: 联调验证帖
                                        - generic [ref=e282]: 0 赞 · 0 评论
                            - link "5 AuthFilter 成功 0 赞 · 0 评论" [ref=e283] [cursor=pointer]:
                                - /url: /posts/d6c2ed87-5809-4929-9046-046bb5972b1e
                                - generic [ref=e284]:
                                    - generic [ref=e285]: '5'
                                    - generic [ref=e286]:
                                        - generic [ref=e287]: AuthFilter 成功
                                        - generic [ref=e288]: 0 赞 · 0 评论
                            - link "6 前后端联调第一篇 0 赞 · 0 评论" [ref=e289] [cursor=pointer]:
                                - /url: /posts/a32a6400-f5ce-4b3d-af39-525b30c42fe6
                                - generic [ref=e290]:
                                    - generic [ref=e291]: '6'
                                    - generic [ref=e292]:
                                        - generic [ref=e293]: 前后端联调第一篇
                                        - generic [ref=e294]: 0 赞 · 0 评论
        - contentinfo [ref=e295]:
            - generic [ref=e296]:
                - generic [ref=e297]:
                    - generic [ref=e298]:
                        - generic [ref=e299]:
                            - generic [ref=e301]: W
                            - generic [ref=e302]: WenxinBlog
                        - paragraph [ref=e303]: 基于 Next.js 14 和 Semi-Design 的现代化博文平台
                        - generic [ref=e304]:
                            - link "github_logo" [ref=e305] [cursor=pointer]:
                                - /url: https://github.com
                                - img "github_logo" [ref=e306]:
                                    - img [ref=e307]
                            - link "twitter" [ref=e310] [cursor=pointer]:
                                - /url: https://twitter.com
                                - img "twitter" [ref=e311]:
                                    - img [ref=e312]
                    - generic [ref=e314]:
                        - heading "产品" [level=3] [ref=e315]
                        - list [ref=e316]:
                            - listitem [ref=e317]:
                                - link "首页" [ref=e318] [cursor=pointer]:
                                    - /url: /
                            - listitem [ref=e319]:
                                - link "博文" [ref=e320] [cursor=pointer]:
                                    - /url: /posts
                            - listitem [ref=e321]:
                                - link "热门" [ref=e322] [cursor=pointer]:
                                    - /url: /trending
                    - generic [ref=e323]:
                        - heading "公司" [level=3] [ref=e324]
                        - list [ref=e325]:
                            - listitem [ref=e326]:
                                - link "关于我们" [ref=e327] [cursor=pointer]:
                                    - /url: /about
                            - listitem [ref=e328]:
                                - link "联系方式" [ref=e329] [cursor=pointer]:
                                    - /url: /contact
                            - listitem [ref=e330]:
                                - link "隐私政策" [ref=e331] [cursor=pointer]:
                                    - /url: /privacy
                    - generic [ref=e332]:
                        - heading "社区" [level=3] [ref=e333]
                        - list [ref=e334]:
                            - listitem [ref=e335]:
                                - link "GitHub" [ref=e336] [cursor=pointer]:
                                    - /url: https://github.com
                            - listitem [ref=e337]:
                                - link "Twitter" [ref=e338] [cursor=pointer]:
                                    - /url: https://twitter.com
                - paragraph [ref=e340]: © 2026 WenxinBlog. All rights reserved.
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
  31  |     await page.waitForURL('/', { timeout: 15000 });
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
> 87  |     await firstPost.click();
      |                     ^ Error: locator.click: Test timeout of 30000ms exceeded.
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
