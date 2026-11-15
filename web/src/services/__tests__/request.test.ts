import { describe, expect, it, vi } from 'vitest'

// 全局 setup 把 vue-i18n 的 createI18n 也 mock 了，真实 locales 模块在测试里无法求值
vi.mock('@/locales', () => ({
  i18n: { global: { t: (key: string) => key } },
}))

import {
  isAuthExpiredError,
  isForbiddenError,
  isRequestCanceledError,
  serverFailureOf,
  shouldIgnoreRequestError,
  takeOverFailure,
  type RequestError,
} from '../request'
import { CODE_UNEXPECTED_ERROR } from '@/constants/api'

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

// 后端写给用户的原文与兜底文案要分得开，拦截器据此决定先不弹、交给执行器
describe('非 2xx 响应体的判定', () => {
  it('带业务码与文案的响应体是后端原文', () => {
    const body = { code: 409, message: '该模型仍被 2 个角色使用，请先将这些角色改绑到其他模型', data: null }
    expect(serverFailureOf(body)).toEqual(body)
  })

  it('未知异常的兜底带独立业务码，不算后端原文', () => {
    expect(serverFailureOf({ code: CODE_UNEXPECTED_ERROR, message: '服务器错误，请联系管理员', data: null })).toBeUndefined()
  })

  it('不是统一信封的响应体（网关页、纯文本）不算', () => {
    expect(serverFailureOf('<html>502 Bad Gateway</html>')).toBeUndefined()
    expect(serverFailureOf({ error: 'Bad Gateway' })).toBeUndefined()
    expect(serverFailureOf(undefined)).toBeUndefined()
  })
})

describe('执行器接管非 2xx 的业务失败', () => {
  it('拿到后端原文，并撤掉拦截器排在下一轮的提示', () => {
    const cancelToast = vi.fn()
    const body = { code: 409, message: '该模型仍被 2 个角色使用', data: null }
    const error = makeError('Request failed with status code 409', { apiResponse: body, cancelToast })

    expect(takeOverFailure(error)).toEqual(body)
    expect(cancelToast).toHaveBeenCalledTimes(1)
  })

  it('不是这类失败时什么都不动', () => {
    expect(takeOverFailure(makeError('Network Error'))).toBeUndefined()
    expect(takeOverFailure('boom')).toBeUndefined()
  })
})
