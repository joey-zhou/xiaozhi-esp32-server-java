import { beforeEach, describe, expect, it, vi } from 'vitest'
import { message } from 'ant-design-vue'

const routerMock = vi.hoisted(() => ({
  push: vi.fn(),
  resolve: vi.fn(),
  currentRoute: { value: { query: {} as Record<string, string> } },
}))

const userStoreMock = vi.hoisted(() => ({
  setUserInfo: vi.fn(),
  setPermissions: vi.fn(),
  setAuthRole: vi.fn(),
  setToken: vi.fn(),
  clearUserInfo: vi.fn(),
  clearToken: vi.fn(),
  hasPermission: vi.fn(() => false),
  hasAnyPermission: vi.fn(() => false),
}))

const userApiMock = vi.hoisted(() => ({
  login: vi.fn(),
  logout: vi.fn(),
  telLogin: vi.fn(),
  register: vi.fn(),
  resetPassword: vi.fn(),
}))

vi.mock('vue-router', () => ({ useRouter: () => routerMock }))
vi.mock('@/store/user', () => ({ useUserStore: () => userStoreMock }))
vi.mock('@/services/user', () => ({
  login: userApiMock.login,
  logout: userApiMock.logout,
  telLogin: userApiMock.telLogin,
  register: userApiMock.register,
  resetPassword: userApiMock.resetPassword,
}))

import { useAuth } from '../useAuth'

function loginResponse(isAdmin: string) {
  return {
    code: 200,
    data: {
      user: { userId: 1, name: 'admin', isAdmin },
      permissions: [],
      authRole: null,
      token: 'token-1',
    },
  }
}

describe('useAuth', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    routerMock.currentRoute.value.query = {}
    routerMock.resolve.mockReturnValue({ meta: {} })
  })

  it('勾选记住我只把用户名写进 localStorage，不落任何形式的密码', async () => {
    userApiMock.login.mockResolvedValue(loginResponse('1'))

    const { login } = useAuth()
    await login({ username: 'admin', password: 'super-secret', rememberMe: true })

    expect(localStorage.getItem('username')).toContain('admin')
    expect(JSON.stringify(localStorage)).not.toContain('super-secret')
    expect(localStorage.getItem('rememberMe')).toBeNull()
  })

  it('取消记住我时清掉已存的用户名', async () => {
    localStorage.setItem('username', 'admin')
    userApiMock.login.mockResolvedValue(loginResponse('1'))

    const { login } = useAuth()
    await login({ username: 'admin', password: 'pwd', rememberMe: false })

    expect(localStorage.getItem('username')).toBe('')
  })

  it('回填只给用户名，并清掉历史版本存下的密码密文', () => {
    localStorage.setItem('username', 'admin')
    localStorage.setItem('rememberMe', 'ciphertext')

    const { getRememberedCredentials } = useAuth()
    const credentials = getRememberedCredentials()

    expect(credentials).toEqual({ username: 'admin', rememberMe: true })
    expect(credentials).not.toHaveProperty('password')
    expect(localStorage.getItem('rememberMe')).toBeNull()
  })

  it('管理员落地仪表盘，普通用户落地设备列表', async () => {
    userApiMock.login.mockResolvedValue(loginResponse('1'))
    const { login } = useAuth()
    await login({ username: 'admin', password: 'pwd' })
    expect(routerMock.push).toHaveBeenLastCalledWith('/dashboard')

    userApiMock.login.mockResolvedValue(loginResponse('0'))
    await login({ username: 'user', password: 'pwd' })
    expect(routerMock.push).toHaveBeenLastCalledWith('/device')
  })

  it('redirect 指向无权限页面时退到 403（与路由守卫的兜底目标保持一致）', async () => {
    routerMock.currentRoute.value.query = { redirect: '/user' }
    routerMock.resolve.mockReturnValue({ path: '/user', fullPath: '/user', meta: { permission: 'system:user' } })
    userStoreMock.hasPermission.mockReturnValue(false)
    userApiMock.login.mockResolvedValue(loginResponse('0'))

    const { login } = useAuth()
    await login({ username: 'user', password: 'pwd' })

    expect(routerMock.push).toHaveBeenCalledWith('/403')
  })

  it('redirect 有权限时按 redirect 跳转，且带 query 的深链原样保留', async () => {
    routerMock.currentRoute.value.query = { redirect: '/user?id=5' }
    routerMock.resolve.mockReturnValue({ meta: { permission: 'system:user' } })
    userStoreMock.hasPermission.mockReturnValue(true)
    userApiMock.login.mockResolvedValue(loginResponse('0'))

    const { login } = useAuth()
    await login({ username: 'user', password: 'pwd' })

    expect(routerMock.push).toHaveBeenCalledWith('/user?id=5')
  })

  it('登录业务码失败时不写会话、不跳转，只弹后端文案', async () => {
    userApiMock.login.mockResolvedValue({ code: 400, data: null, message: '用户名或密码错误' })

    const { login } = useAuth()

    expect(await login({ username: 'admin', password: 'wrong' })).toBe(false)
    expect(userStoreMock.setToken).not.toHaveBeenCalled()
    expect(routerMock.push).not.toHaveBeenCalled()
    expect(message.error).toHaveBeenCalledWith('用户名或密码错误')
  })

  it('HTTP 失败已由拦截器提示，登录不再叠第二条', async () => {
    userApiMock.login.mockRejectedValue(new Error('Request failed with status code 400'))

    const { login } = useAuth()

    expect(await login({ username: 'admin', password: 'wrong' })).toBe(false)
    expect(userStoreMock.setToken).not.toHaveBeenCalled()
    expect(routerMock.push).not.toHaveBeenCalled()
    expect(message.error).not.toHaveBeenCalled()
  })

  it('手机号未注册时跳注册页', async () => {
    userApiMock.telLogin.mockResolvedValue({ code: 201, message: '手机号未注册' })

    const { telLogin } = useAuth()
    expect(await telLogin({ tel: '13800000000', code: '123456' })).toBe(false)
    expect(routerMock.push).toHaveBeenCalledWith('/register')
  })

  it('账号密码登录与手机号登录写入同一套会话状态', async () => {
    userApiMock.login.mockResolvedValue(loginResponse('1'))
    userApiMock.telLogin.mockResolvedValue(loginResponse('1'))

    const { login, telLogin } = useAuth()
    await login({ username: 'admin', password: 'pwd' })
    await telLogin({ tel: '13800000000', code: '123456' })

    expect(userStoreMock.setToken).toHaveBeenCalledTimes(2)
    expect(userStoreMock.setPermissions).toHaveBeenCalledTimes(2)
    expect(userStoreMock.setAuthRole).toHaveBeenCalledTimes(2)
    expect(routerMock.push).toHaveBeenNthCalledWith(1, '/dashboard')
    expect(routerMock.push).toHaveBeenNthCalledWith(2, '/dashboard')
  })
})
