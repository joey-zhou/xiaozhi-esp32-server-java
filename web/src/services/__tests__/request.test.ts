import { describe, expect, it, vi } from 'vitest'

// 全局 setup 把 vue-i18n 的 createI18n 也 mock 了，真实 locales 模块在测试里无法求值
vi.mock('@/locales', () => ({
  i18n: { global: { t: (key: string) => key } },
}))

import {
  isAuthExpiredError,
  isForbiddenError,
  isRequestCanceledError,
  shouldIgnoreRequestError,
  type RequestError,
} from '../request'

function makeError(message: string, extras: Partial<RequestError> = {}): RequestError {
  return Object.assign(new Error(message), extras) as RequestError
}

describe('请求错误判定', () => {
  it('取消错误按标记位、错误码与文案三种形态识别', () => {
    expect(isRequestCanceledError(makeError('boom', { isRequestCanceled: true }))).toBe(true)
    expect(isRequestCanceledError(makeError('boom', { code: 'ERR_CANCELED' }))).toBe(true)
    expect(isRequestCanceledError(makeError('request canceled'))).toBe(true)
    expect(isRequestCanceledError(makeError('signal is aborted'))).toBe(true)
    expect(isRequestCanceledError(makeError('boom'))).toBe(false)
  })

  it('非 Error 值一律不算取消', () => {
    expect(isRequestCanceledError('canceled')).toBe(false)
    expect(isRequestCanceledError(null)).toBe(false)
    expect(isRequestCanceledError(undefined)).toBe(false)
  })

  it('登录过期与权限不足各认各的标记位', () => {
    expect(isAuthExpiredError(makeError('boom', { isAuthExpired: true }))).toBe(true)
    expect(isAuthExpiredError(makeError('boom', { isForbidden: true }))).toBe(false)
    expect(isForbiddenError(makeError('boom', { isForbidden: true }))).toBe(true)
    expect(isForbiddenError(makeError('boom', { isAuthExpired: true }))).toBe(false)
  })

  // useTable / useRequest / ErrorBoundary 都靠这一条来决定「要不要弹提示」
  it('取消、登录过期、静默三类错误都要被忽略，403 与普通错误不忽略', () => {
    expect(shouldIgnoreRequestError(makeError('boom', { isRequestCanceled: true }))).toBe(true)
    expect(shouldIgnoreRequestError(makeError('boom', { isAuthExpired: true }))).toBe(true)
    expect(shouldIgnoreRequestError(makeError('boom', { isSilent: true }))).toBe(true)
    expect(shouldIgnoreRequestError(makeError('boom', { isForbidden: true }))).toBe(false)
    expect(shouldIgnoreRequestError(makeError('Request failed with status code 500'))).toBe(false)
  })
})
