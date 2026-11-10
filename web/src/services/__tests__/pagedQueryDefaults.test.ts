// 纯文件系统扫描，不需要 DOM；jsdom 环境下 import.meta.url 不是 file: scheme
// @vitest-environment node
import { readFileSync, readdirSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const SRC_ROOT = fileURLToPath(new URL('../../', import.meta.url))
const SERVICES_DIR = join(SRC_ROOT, 'services')

/**
 * 走 http.getPage 的接口，后端不给分页参数时只返回第一页（默认 10 条）。
 * 调用方拿它填下拉框或做名称回显时，超出首页的数据会静默消失——
 * 表现是「已绑定的角色显示成未知角色（id）」这类查不出原因的故障。
 */
function pagedFunctionNames(): string[] {
  const names: string[] = []
  for (const entry of readdirSync(SERVICES_DIR, { withFileTypes: true })) {
    if (!entry.isFile() || !entry.name.endsWith('.ts')) continue
    const source = readFileSync(join(SERVICES_DIR, entry.name), 'utf-8')
    // export function xxx(...) { ... http.getPage ... }，函数体到下一个 export 为止
    const blocks = source.split(/^export /m)
    for (const block of blocks) {
      if (!block.includes('http.getPage')) continue
      const match = block.match(/^function\s+([A-Za-z0-9_]+)/)
      if (match?.[1]) names.push(match[1])
    }
  }
  return names
}

function collectSources(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (entry.name === '__tests__') continue
      collectSources(join(dir, entry.name), out)
    } else if (/\.(vue|ts)$/.test(entry.name)) {
      out.push(join(dir, entry.name))
    }
  }
  return out
}

describe('分页接口的调用方必须显式给分页', () => {
  it('识别得到走 getPage 的接口', () => {
    // 门槛防假绿：解析不出函数名时下面那条规则会恒绿
    expect(pagedFunctionNames().length).toBeGreaterThanOrEqual(10)
  })

  it('没有任何地方用空参调用分页接口', () => {
    const names = pagedFunctionNames()
    const emptyCall = new RegExp(`\\b(${names.join('|')})\\(\\s*\\{\\s*\\}\\s*\\)`)
    const violations: string[] = []

    for (const file of collectSources(SRC_ROOT)) {
      const source = readFileSync(file, 'utf-8')
      const hit = source.match(emptyCall)
      if (hit) violations.push(`${relative(SRC_ROOT, file)} → ${hit[0]}`)
    }

    expect(violations).toEqual([])
  })
})
