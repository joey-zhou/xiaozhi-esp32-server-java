// 纯文件系统扫描，不需要 DOM；jsdom 环境下 import.meta.url 不是 file: scheme
// @vitest-environment node
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const WEB_ROOT = fileURLToPath(new URL('../../../', import.meta.url))
const PUBLIC_DIR = join(WEB_ROOT, 'public')

/** 只扫视图与布局：services 里有一批「按顺序试」的候选路径，缺失是预期内的 */
const SCAN_DIRS = ['src/views', 'src/layouts']

/** 以 / 开头、带扩展名的静态资源引用，构建时不会被 Vite 解析，必须在 public 下真实存在 */
const ASSET_REF = /["'(]\/(?!\/)([A-Za-z0-9_\-./]+\.(?:png|jpe?g|gif|svg|ico|webp))["')]/g

function collectFiles(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (entry.name === '__tests__') continue
      collectFiles(join(dir, entry.name), out)
    } else if (/\.(vue|ts)$/.test(entry.name)) {
      out.push(join(dir, entry.name))
    }
  }
  return out
}

describe('public 静态资源引用', () => {
  it('视图与布局里引用的图片在 public 下都存在', () => {
    const missing: string[] = []

    for (const scanDir of SCAN_DIRS) {
      for (const file of collectFiles(join(WEB_ROOT, scanDir))) {
        const content = readFileSync(file, 'utf8')
        let match: RegExpExecArray | null
        ASSET_REF.lastIndex = 0
        while ((match = ASSET_REF.exec(content)) !== null) {
          const assetPath = match[1]!
          if (existsSync(join(PUBLIC_DIR, assetPath))) continue
          const line = content.slice(0, match.index).split('\n').length
          missing.push(`/${assetPath} <- ${relative(WEB_ROOT, file)}:${line}`)
        }
      }
    }

    expect(missing, '这些路径在 public 下没有对应文件，运行时是 404').toEqual([])
  })
})
