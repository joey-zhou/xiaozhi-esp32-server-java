// 纯文件系统扫描，不需要 DOM；jsdom 环境下 import.meta.url 不是 file: scheme
// @vitest-environment node
import { readFileSync, readdirSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import zhCN from '../zh-CN'
import enUS from '../en-US'

const SRC_ROOT = fileURLToPath(new URL('../../', import.meta.url))

/** 不参与 key 引用扫描的目录：语言包自身与测试 */
const SKIP_DIRS = new Set(['locales', '__tests__'])

type LocaleTree = { [key: string]: string | LocaleTree }

/** 把嵌套语言包拍平成 `a.b.c` -> 文案 */
function flatten(tree: LocaleTree, prefix = '', out = new Map<string, string>()): Map<string, string> {
  for (const [key, value] of Object.entries(tree)) {
    const fullKey = prefix ? `${prefix}.${key}` : key
    if (typeof value === 'string') {
      out.set(fullKey, value)
    } else {
      flatten(value, fullKey, out)
    }
  }
  return out
}

/** 取文案里的 {name} 占位符，排序后拼成可比较的串 */
function placeholders(text: string): string {
  return [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join(',')
}

function collectSourceFiles(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (SKIP_DIRS.has(entry.name)) continue
      collectSourceFiles(join(dir, entry.name), out)
    } else if (/\.(vue|ts)$/.test(entry.name)) {
      out.push(join(dir, entry.name))
    }
  }
  return out
}

/**
 * 扫描源码里 `t('a.b')` / `$t('a.b')` 的字面量 key
 * 反引号拼接出来的动态 key 扫不到，属于有意放过
 */
function collectUsedKeys(): Map<string, string> {
  const used = new Map<string, string>()
  for (const file of collectSourceFiles(SRC_ROOT)) {
    const content = readFileSync(file, 'utf8')
    const pattern = /(?:\$t|\bt)\(\s*'([A-Za-z0-9_.]+)'/g
    let match: RegExpExecArray | null
    while ((match = pattern.exec(content)) !== null) {
      const key = match[1]!
      // 单段的不是语言包 key（例如 t('x') 形式的工具函数调用）
      if (!key.includes('.')) continue
      if (used.has(key)) continue
      const line = content.slice(0, match.index).split('\n').length
      used.set(key, `${relative(SRC_ROOT, file)}:${line}`)
    }
  }
  return used
}

/**
 * 收集「作为数据写在配置里、由组件动态 t() 出去」的 key。
 * providerConfig 的 label / help / placeholder 就是这种形态，扫 t('...') 扫不到，
 * 曾因此漏掉 130 条 config.help.*，配置页整列帮助文字显示成裸 key。
 */
function collectDataKeys(topLevelNamespaces: Set<string>): Map<string, string> {
  const used = new Map<string, string>()
  for (const file of collectSourceFiles(SRC_ROOT)) {
    const content = readFileSync(file, 'utf8')
    const pattern = /(?:label|help|placeholder|title|tooltip|dragText|dragHint|successMessage)\s*:\s*'([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+)'/g
    let match: RegExpExecArray | null
    while ((match = pattern.exec(content)) !== null) {
      const key = match[1]!
      // 只认首段命中语言包顶层命名空间的，避免把路由路径、CSS 类名之类误判成文案 key
      if (!topLevelNamespaces.has(key.split('.')[0]!)) continue
      if (used.has(key)) continue
      const line = content.slice(0, match.index).split('\n').length
      used.set(key, `${relative(SRC_ROOT, file)}:${line}`)
    }
  }
  return used
}

const zhKeys = flatten(zhCN as LocaleTree)
const enKeys = flatten(enUS as LocaleTree)

describe('locale 对齐', () => {
  it('zh-CN 与 en-US 的 key 集合完全一致', () => {
    const onlyZh = [...zhKeys.keys()].filter((key) => !enKeys.has(key))
    const onlyEn = [...enKeys.keys()].filter((key) => !zhKeys.has(key))

    expect(
      { onlyZh, onlyEn },
      `zh-CN 独有 ${onlyZh.length} 个、en-US 独有 ${onlyEn.length} 个，两个语言包必须同增同删`,
    ).toEqual({ onlyZh: [], onlyEn: [] })
  })

  it('同一个 key 在两个语言包里的占位符一致', () => {
    const mismatched = [...zhKeys.entries()]
      .filter(([key, text]) => {
        const enText = enKeys.get(key)
        return enText !== undefined && placeholders(text) !== placeholders(enText)
      })
      .map(([key, text]) => `${key}: zh {${placeholders(text)}} / en {${placeholders(enKeys.get(key)!)}}`)

    expect(mismatched, '占位符对不上会让某一种语言渲染出未替换的 {xxx}').toEqual([])
  })

  it("源码里 t('...') 用到的 key 都在语言包里", () => {
    const missing = [...collectUsedKeys().entries()]
      .filter(([key]) => !zhKeys.has(key))
      .map(([key, where]) => `${key} <- ${where}`)

    expect(
      missing,
      '这些 key 只在代码里出现、语言包里没有，界面会直接显示裸 key；新增文案必须同时补进 zh-CN 和 en-US',
    ).toEqual([])
  })

  it('配置数据里当作文案用的 key 也都在语言包里', () => {
    const namespaces = new Set([...zhKeys.keys()].map((key) => key.split('.')[0]!))
    const missing = [...collectDataKeys(namespaces).entries()]
      .filter(([key]) => !zhKeys.has(key))
      .map(([key, where]) => `${key} <- ${where}`)

    expect(
      missing,
      'providerConfig 这类把 key 写成数据字段的地方，静态扫 t() 抓不到，必须单独对账',
    ).toEqual([])
  })
})
