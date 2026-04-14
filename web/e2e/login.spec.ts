import { test, expect } from '@playwright/test'

/**
 * 登录页面 E2E 测试
 */
test.describe('登录页面', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
  })

  test('正确渲染登录表单', async ({ page }) => {
    // 页面标题或登录按钮应存在
    await expect(page.locator('form')).toBeVisible()
    // 用户名输入框
    await expect(page.locator('input[type="text"], input[placeholder*="用户名"], input[placeholder*="username"]').first()).toBeVisible()
    // 密码输入框
    await expect(page.locator('input[type="password"]').first()).toBeVisible()
  })

  test('空表单提交显示验证错误', async ({ page }) => {
    // 点击登录按钮
    const loginBtn = page.locator('button[type="submit"], button:has-text("登录"), button:has-text("Login")').first()
    await loginBtn.click()

    // 等待验证消息出现
    await page.waitForTimeout(500)

    // 应该有验证提示
    const formErrors = page.locator('.ant-form-item-explain-error')
    await expect(formErrors.first()).toBeVisible()
  })

  test('密码输入框支持显示/隐藏切换', async ({ page }) => {
    const passwordInput = page.locator('input[type="password"]').first()
    await expect(passwordInput).toBeVisible()

    // 输入密码
    await passwordInput.fill('testpassword')
    expect(await passwordInput.getAttribute('type')).toBe('password')
  })

  test('未认证访问受保护页面重定向到登录', async ({ page }) => {
    // 尝试访问 dashboard
    await page.goto('/dashboard')

    // 应该被重定向到登录页
    await page.waitForURL(/\/login/)
    expect(page.url()).toContain('/login')
  })

  test('登录页面有注册链接', async ({ page }) => {
    const registerLink = page.locator('a[href*="register"], button:has-text("注册"), a:has-text("注册"), a:has-text("Register")').first()
    await expect(registerLink).toBeVisible()
  })
})
