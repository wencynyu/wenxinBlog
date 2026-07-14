import { test, expect } from '@playwright/test';

const TEST_USER = { email: 'dave2@wenxinblog.com', password: 'password123' };

test.describe('核心联调流程', () => {
  test('首页加载并显示真实博文', async ({ page }) => {
    await page.goto('/');
    // 等待帖子加载
    await page.waitForTimeout(5000);
    // 应该有博文卡片（不是 EmptyState）
    const postCards = page.locator('article');
    await expect(postCards.first()).toBeVisible({ timeout: 10000 });
    const count = await postCards.count();
    expect(count).toBeGreaterThan(0);
    // 不应包含"演示数据"文本
    const body = await page.textContent('body');
    expect(body).not.toContain('演示数据');
    expect(body).not.toContain('MOCK');
  });

  test('注册新用户', async ({ page }) => {
    const unique = `e2e_${Date.now()}`;
    await page.goto('/register');
    await page.waitForSelector('#username', { timeout: 10000 });
    await page.fill('#username', unique);
    await page.fill('#displayName', unique);
    await page.fill('#email', `${unique}@test.com`);
    await page.fill('#password', 'password123');
    await page.fill('#confirmPassword', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForURL('/', { timeout: 15000 });
  });

  test('登录 → 首页显示登录态', async ({ page }) => {
    await page.goto('/login');
    await page.waitForSelector('#email', { timeout: 10000 });
    await page.fill('#email', TEST_USER.email);
    await page.fill('#password', TEST_USER.password);
    await page.click('button[type="submit"]');
    await page.waitForURL('/', { timeout: 15000 });
    await expect(page.locator('text=写博文')).toBeVisible({ timeout: 10000 });
  });

  test('登录 → 发帖 → 详情页', async ({ page }) => {
    // 登录
    await page.goto('/login');
    await page.waitForSelector('#email', { timeout: 10000 });
    await page.fill('#email', TEST_USER.email);
    await page.fill('#password', TEST_USER.password);
    await page.click('button[type="submit"]');
    await page.waitForURL('/', { timeout: 15000 });

    // 进编辑器
    await page.click('a[href="/editor"]');
    await page.waitForURL('/editor', { timeout: 10000 });

    // 填写博文
    const title = `E2E测试帖_${Date.now()}`;
    await page.waitForSelector('input[class*="text-3xl"]', { timeout: 5000 });
    await page.fill('input[class*="text-3xl"]', title);
    const textarea = page.locator('textarea').first();
    await textarea.fill('## E2E 自动化测试\n这是一个由 Playwright 自动创建的测试博文。');

    // 发布
    await page.click('button:has-text("发布")');
    await page.waitForURL(/\/posts\//, { timeout: 20000 });

    // 验证详情页内容
    await expect(page.locator('h1')).toContainText(title, { timeout: 15000 });
    // 作者名不应是纯 UUID
    const body = await page.textContent('body');
    expect(body).not.toMatch(/author.*[0-9a-f]{8}-[0-9a-f]{4}/);
  });

  test('详情页 → 评论', async ({ page }) => {
    // 登录
    await page.goto('/login');
    await page.waitForSelector('#email', { timeout: 10000 });
    await page.fill('#email', TEST_USER.email);
    await page.fill('#password', TEST_USER.password);
    await page.click('button[type="submit"]');
    await page.waitForTimeout(8000);
    await page.goto('/');
    await page.waitForTimeout(5000);

    // 点第一个帖子
    const firstPost = page.locator('a[href^="/posts/"]').first();
    await firstPost.click();
    await page.waitForTimeout(5000);

    // 发表评论
    const commentInput = page.locator('input[placeholder*="评论"]').first();
    await commentInput.waitFor({ timeout: 10000 });
    await commentInput.fill('E2E 自动评论');
    await page.click('button:has-text("发送")');
    await page.waitForTimeout(3000);
    const body = await page.textContent('body');
    expect(body).toContain('E2E 自动评论');
  });

  test('刷新页面保持登录态', async ({ page }) => {
    // 登录
    await page.goto('/login');
    await page.waitForSelector('#email', { timeout: 10000 });
    await page.fill('#email', TEST_USER.email);
    await page.fill('#password', TEST_USER.password);
    await page.click('button[type="submit"]');
    await page.waitForURL('/', { timeout: 15000 });

    // 刷新
    await page.reload();
    await page.waitForTimeout(5000);
    await expect(page.locator('text=写博文')).toBeVisible({ timeout: 10000 });
  });
});
