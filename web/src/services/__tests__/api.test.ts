import { describe, expect, it } from 'vitest'

import api from '../api'

/**
 * 还没收敛的命名空间：mcpTool 下的 key 会各自拼接不同的子路径后缀（role/xxx、global/xxx），
 * 值相同是合理复用而非误标的 CRUD 别名，不纳入去重检查
 */
const PENDING_NAMESPACES = new Set(['mcpTool'])

function collectPaths(value: unknown): string[] {
  if (typeof value === 'string') return [value]
  if (value && typeof value === 'object') {
    return Object.values(value as Record<string, unknown>).flatMap(collectPaths)
  }
  return []
}

describe('接口路径表', () => {
  it('同一命名空间里不给同一条路径起多个别名', () => {
    const duplicated = Object.entries(api)
      .filter(([namespace]) => !PENDING_NAMESPACES.has(namespace))
      .filter(([, value]) => {
        const paths = collectPaths(value)
        return new Set(paths).size !== paths.length
      })
      .map(([namespace]) => namespace)

    expect(
      duplicated,
      '按 CRUD 动作起的别名值全一样，只会让人以为 query 常量不能拿来发 POST',
    ).toEqual([])
  })

  it('不登记后端没有的导出接口', () => {
    // MessageController / DeviceController 都没有 /export，登记了只会 404
    expect(collectPaths(api).filter((path) => path.endsWith('/export'))).toEqual([])
  })

  it('资源根路径与后端 Controller 的 RequestMapping 一致', () => {
    expect(api.device).toBe('/device')
    expect(api.template).toBe('/template')
    expect(api.authRole).toBe('/auth-role')
    expect(api.user.root).toBe('/user')
    expect(api.role.root).toBe('/role')
    expect(api.message.root).toBe('/message')
    expect(api.config.root).toBe('/config')
  })
})
