import { test, expect } from '@playwright/test'

/**
 * 页面导航 E2E 测试
 */
test.describe('路由导航', () => {
  test('访问不存在的路由跳转到 404', async ({ page }) => {
    await page.goto('/nonexistent-page-xyz')
    // 等待路由跳转完成
    await page.waitForTimeout(1000)
    // 应该显示 404 页面或被重定向
    const url = page.url()
    expect(url).toMatch(/\/(404|login)/)
  })

  test('登录页可正常访问', async ({ page }) => {
    const response = await page.goto('/login')
    expect(response?.status()).toBe(200)
  })

  test('注册页可正常访问', async ({ page }) => {
    const response = await page.goto('/register')
    expect(response?.status()).toBe(200)
  })

  test('403 页面可正常访问', async ({ page }) => {
    const response = await page.goto('/403')
    expect(response?.status()).toBe(200)
  })
})

test.describe('页面基础结构', () => {
  test('登录页有正确的 HTML 结构', async ({ page }) => {
    await page.goto('/login')
    // 页面应有 #app 根节点
    await expect(page.locator('#app')).toBeVisible()
  })

  test('页面正确加载 CSS 和 JS', async ({ page }) => {
    const response = await page.goto('/login')
    expect(response?.status()).toBe(200)

    // 检查页面没有 JS 报错
    const errors: string[] = []
    page.on('pageerror', (err) => errors.push(err.message))
    await page.waitForTimeout(2000)
    // 允许某些已知的非关键错误（如 ResizeObserver）
    const criticalErrors = errors.filter(
      (e) => !e.includes('ResizeObserver') && !e.includes('Script error'),
    )
    expect(criticalErrors).toHaveLength(0)
  })
})
