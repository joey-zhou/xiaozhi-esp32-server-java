import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'

/**
 * ant-design-vue 走按需引入（vite.config.ts 里的 unplugin-vue-components）后，
 * 解析器认不出的组件**不会让构建失败**，只会退化成运行期 resolveComponent，
 * 到浏览器里才报 "Failed to resolve component" —— 页面上那块直接是空的。
 *
 * 所以这里直接拿构建用的同一个解析器，逐个验证模板里出现过的每个 <a-xxx>。
 * 新用一个解析器认不出的组件时，这条会红在 CI 而不是红在用户屏幕上。
 */
describe('ant-design-vue 按需引入', () => {
  const SRC = join(__dirname, '..')

  function collectVueFiles(dir: string, out: string[] = []): string[] {
    for (const entry of readdirSync(dir)) {
      const full = join(dir, entry)
      if (statSync(full).isDirectory()) {
        if (entry !== '__tests__' && entry !== 'node_modules') collectVueFiles(full, out)
      } else if (entry.endsWith('.vue')) {
        out.push(full)
      }
    }
    return out
  }

  /** 只扫 <template> 段，避免把 script 里的字符串误当成标签 */
  function antdTagsOf(file: string): string[] {
    const template = readFileSync(file, 'utf8').split('</template>')[0] ?? ''
    const tags: string[] = []
    for (const match of template.matchAll(/<(a-[a-z0-9-]+)/g)) {
      if (match[1]) tags.push(match[1])
    }
    return tags
  }

  const tags = [...new Set(collectVueFiles(SRC).flatMap(antdTagsOf))].sort()

  it('模板里确实扫到了 antd 组件（扫不到说明判定面已失效）', () => {
    expect(tags.length).toBeGreaterThan(30)
  })

  it('模板里用到的每个 antd 组件都能被解析器认出', async () => {
    const resolver = AntDesignVueResolver({ importStyle: false, resolveIcons: false })
    const resolve = typeof resolver === 'function' ? resolver : resolver.resolve
    const toPascal = (tag: string) =>
      tag.split('-').map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join('')

    const unresolved: string[] = []
    for (const tag of tags) {
      if (!(await resolve(toPascal(tag)))) unresolved.push(tag)
    }

    expect(unresolved).toEqual([])
  })
})
