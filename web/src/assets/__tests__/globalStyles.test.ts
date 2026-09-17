// 纯文件系统扫描，不需要 DOM；jsdom 环境下 import.meta.url 不是 file: scheme
// @vitest-environment node
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const ASSETS_ROOT = fileURLToPath(new URL('../', import.meta.url))

/** 十六进制颜色字面量，排除 #app 这类 id 选择器 */
const HEX_COLOR = /#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3,4})(?![0-9a-zA-Z_-])/

function readAsset(name: string): string {
  return readFileSync(join(ASSETS_ROOT, name), 'utf-8')
}

/** 把 css 拆成「选择器 + 声明块」，够用来做规则级断言 */
function parseRules(css: string): Array<{ selector: string; body: string }> {
  return css
    .split('}')
    .map((chunk) => {
      const [selector, body] = chunk.split('{')
      return { selector: (selector ?? '').trim(), body: (body ?? '').trim() }
    })
    .filter((rule) => rule.body.length > 0)
}

describe('全局样式基线', () => {
  it('脚手架残留的 base.css 与整套 --vt-c-/--color-* 调色板不再存在', () => {
    expect(existsSync(join(ASSETS_ROOT, 'base.css'))).toBe(false)

    for (const name of ['main.css', 'theme.css']) {
      const css = readAsset(name)
      expect(css).not.toContain('--vt-c-')
      expect(css).not.toMatch(/--color-(background|text|heading|border)/)
      expect(css).not.toContain('prefers-color-scheme')
    }
  })

  it('过渡只加在容器层，不存在作用于所有元素的 * 过渡', () => {
    for (const name of ['main.css', 'theme.css']) {
      const universalTransitions = parseRules(readAsset(name)).filter(
        (rule) =>
          rule.selector.split(',').some((part) => part.trim() === '*') &&
          rule.body.includes('transition')
      )
      expect(universalTransitions).toHaveLength(0)
    }
  })

  it('theme.css 的颜色只走 --ant-* 变量，不写死十六进制', () => {
    expect(readAsset('theme.css')).not.toMatch(HEX_COLOR)
  })
})
