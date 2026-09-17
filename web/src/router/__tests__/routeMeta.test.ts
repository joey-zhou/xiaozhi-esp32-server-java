import { describe, expect, it, vi } from 'vitest'
import zhCN from '@/locales/zh-CN'
import enUS from '@/locales/en-US'

// 路由表静态引 MainLayout，本用例只关心 meta，不需要真实布局
vi.mock('@/layouts/MainLayout.vue', () => ({ default: { template: '<div />' } }))

import router from '../index'

type LocaleTree = { [key: string]: string | LocaleTree }

/** 按 `a.b.c` 逐段下钻，取不到文案就返回 undefined */
function lookup(tree: LocaleTree, key: string): string | undefined {
  let current: string | LocaleTree | undefined = tree
  for (const segment of key.split('.')) {
    if (typeof current !== 'object') return undefined
    current = current[segment]
  }
  return typeof current === 'string' ? current : undefined
}

/** 路由 meta 里所有当成文案 key 用的字段：title 进浏览器标题，parent 进菜单分组 */
function collectMetaKeys(): { key: string; where: string }[] {
  const keys: { key: string; where: string }[] = []
  for (const route of router.getRoutes()) {
    for (const field of ['title', 'parent'] as const) {
      const value = route.meta?.[field]
      if (typeof value === 'string' && value.startsWith('router.')) {
        keys.push({ key: value, where: `${String(route.name ?? route.path)}.meta.${field}` })
      }
    }
  }
  return keys
}

describe('路由 meta 文案 key', () => {
  it('收集到了子路由的 meta key', () => {
    const keys = collectMetaKeys().map(({ key }) => key)

    expect(keys).toContain('router.title.dashboard')
    expect(keys).toContain('router.parent.configManagement')
  })

  it('title / parent 的 key 在两个语言包里都有文案', () => {
    const missing = collectMetaKeys()
      .filter(({ key }) => !lookup(zhCN as LocaleTree, key) || !lookup(enUS as LocaleTree, key))
      .map(({ key, where }) => `${key} <- ${where}`)

    expect(
      missing,
      'meta.title 由路由守卫直接 i18n.global.t()，缺 key 时浏览器标题会显示裸 key',
    ).toEqual([])
  })
})
