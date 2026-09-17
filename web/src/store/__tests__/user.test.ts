import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '../user'
import type { User } from '@/types/user'
import type { AuthRole, PermissionTreeNode } from '@/types/authRole'

describe('useUserStore', () => {
  let store: ReturnType<typeof useUserStore>

  beforeEach(() => {
    setActivePinia(createPinia())
    store = useUserStore()
    // 清理 localStorage
    localStorage.clear()
  })

  // useStorage 会挂 storage 事件监听，不销毁会跨用例污染身份状态
  afterEach(() => {
    store.$dispose()
  })

  describe('初始状态', () => {
    it('userInfo 默认为 null', () => {
      expect(store.userInfo).toBeNull()
    })

    it('permissions 默认为空数组', () => {
      expect(store.permissions).toEqual([])
    })

    it('authRole 默认为 null', () => {
      expect(store.authRole).toBeNull()
    })

    it('token 默认为空字符串', () => {
      expect(store.token).toBe('')
    })

    it('isAdmin 默认为 false', () => {
      expect(store.isAdmin).toBe(false)
    })
  })

  describe('setUserInfo / updateUserInfo / clearUserInfo', () => {
    const mockUser: User = {
      userId: 1,
      username: 'admin',
      name: '管理员',
      isAdmin: '1',
    }

    it('设置用户信息', () => {
      store.setUserInfo(mockUser)
      expect(store.userInfo).toEqual(mockUser)
    })

    it('更新部分用户信息', () => {
      store.setUserInfo(mockUser)
      store.updateUserInfo({ name: '新名字', email: 'new@test.com' })
      expect(store.userInfo?.name).toBe('新名字')
      expect(store.userInfo?.email).toBe('new@test.com')
      expect(store.userInfo?.username).toBe('admin') // 保留原有字段
    })

    it('updateUserInfo 在 userInfo 为 null 时不操作', () => {
      store.clearUserInfo() // 确保 userInfo 为 null
      store.updateUserInfo({ name: '新名字' })
      expect(store.userInfo).toBeNull()
    })

    it('清除用户信息', () => {
      store.setUserInfo(mockUser)
      store.setPermissions([{ permissionId: 1, name: 'test', permissionKey: 'test', permissionType: 'menu' }])
      store.setAuthRole({ authRoleId: 1, authRoleName: '管理员', roleKey: 'admin' })

      store.clearUserInfo()

      expect(store.userInfo).toBeNull()
      expect(store.permissions).toEqual([])
      expect(store.authRole).toBeNull()
    })
  })

  describe('isAdmin 计算属性', () => {
    it('管理员用户返回 true', () => {
      store.setUserInfo({ userId: 1, isAdmin: '1' })
      expect(store.isAdmin).toBe(true)
    })

    it('普通用户返回 false', () => {
      store.setUserInfo({ userId: 2, isAdmin: '0' })
      expect(store.isAdmin).toBe(false)
    })

    it('isAdmin 未设置时返回 false', () => {
      store.setUserInfo({ userId: 3 })
      expect(store.isAdmin).toBe(false)
    })
  })

  describe('权限检查', () => {
    const mockPermissions: PermissionTreeNode[] = [
      { permissionId: 1, name: '设备管理', permissionKey: 'device:list', permissionType: 'menu' },
      { permissionId: 2, name: '设备添加', permissionKey: 'device:add', permissionType: 'button' },
      { permissionId: 3, name: '用户管理', permissionKey: 'user:list', permissionType: 'menu' },
    ]

    describe('hasPermission', () => {
      it('管理员拥有所有权限', () => {
        store.setUserInfo({ userId: 1, isAdmin: '1' })
        expect(store.hasPermission('any:permission')).toBe(true)
        expect(store.hasPermission('nonexistent')).toBe(true)
      })

      it('普通用户检查具体权限', () => {
        store.setUserInfo({ userId: 2, isAdmin: '0' })
        store.setPermissions(mockPermissions)

        expect(store.hasPermission('device:list')).toBe(true)
        expect(store.hasPermission('device:add')).toBe(true)
        expect(store.hasPermission('device:delete')).toBe(false)
      })

      it('无权限时返回 false', () => {
        store.setUserInfo({ userId: 2, isAdmin: '0' })
        store.setPermissions([])
        expect(store.hasPermission('device:list')).toBe(false)
      })
    })

    // 后端下发的是树：菜单是 root，按钮/接口权限挂在 children 上。
    // hasPermission 唯一非平凡的逻辑就是把整棵树拍平，深层节点查不到等于全站按钮权限失效
    describe('权限树拍平', () => {
      const treePermissions: PermissionTreeNode[] = [
        {
          permissionId: 1,
          name: '系统管理',
          permissionKey: 'system',
          permissionType: 'menu',
          children: [
            {
              permissionId: 2,
              name: '设备管理',
              permissionKey: 'system:device',
              permissionType: 'menu',
              children: [
                { permissionId: 3, name: '新增', permissionKey: 'system:device:create', permissionType: 'button' },
                { permissionId: 4, name: '发送', permissionKey: 'system:device:api:send', permissionType: 'api' },
              ],
            },
          ],
        },
      ]

      it('第三层的按钮/接口权限也能查到', () => {
        store.setUserInfo({ userId: 2, isAdmin: '0' })
        store.setPermissions(treePermissions)

        expect(store.hasPermission('system')).toBe(true)
        expect(store.hasPermission('system:device')).toBe(true)
        expect(store.hasPermission('system:device:create')).toBe(true)
        expect(store.hasPermission('system:device:api:send')).toBe(true)
        expect(store.hasPermission('system:device:delete')).toBe(false)
      })

      it('没有 permissionKey 的中间节点不会污染集合', () => {
        store.setUserInfo({ userId: 2, isAdmin: '0' })
        store.setPermissions([
          {
            permissionId: 10,
            name: '无 key 的分组',
            permissionKey: '',
            permissionType: 'menu',
            children: [
              { permissionId: 11, name: '导出', permissionKey: 'system:user:export', permissionType: 'button' },
            ],
          },
        ])

        expect(store.hasPermission('')).toBe(false)
        expect(store.hasPermission('system:user:export')).toBe(true)
      })

      it('换一批权限后旧的立即失效', () => {
        store.setUserInfo({ userId: 2, isAdmin: '0' })
        store.setPermissions(treePermissions)
        expect(store.hasPermission('system:device:create')).toBe(true)

        store.setPermissions([])
        expect(store.hasPermission('system:device:create')).toBe(false)
      })
    })

    describe('hasAnyPermission', () => {
      it('管理员始终返回 true', () => {
        store.setUserInfo({ userId: 1, isAdmin: '1' })
        expect(store.hasAnyPermission(['nonexistent'])).toBe(true)
      })

      it('有任一权限即返回 true', () => {
        store.setUserInfo({ userId: 2, isAdmin: '0' })
        store.setPermissions(mockPermissions)

        expect(store.hasAnyPermission(['device:list', 'device:delete'])).toBe(true)
        expect(store.hasAnyPermission(['device:delete', 'role:list'])).toBe(false)
      })
    })

    describe('hasAllPermissions', () => {
      it('管理员始终返回 true', () => {
        store.setUserInfo({ userId: 1, isAdmin: '1' })
        expect(store.hasAllPermissions(['a', 'b', 'c'])).toBe(true)
      })

      it('需全部具备才返回 true', () => {
        store.setUserInfo({ userId: 2, isAdmin: '0' })
        store.setPermissions(mockPermissions)

        expect(store.hasAllPermissions(['device:list', 'device:add'])).toBe(true)
        expect(store.hasAllPermissions(['device:list', 'device:delete'])).toBe(false)
      })
    })
  })

  describe('Token 管理', () => {
    it('设置和清除 token', () => {
      store.setToken('test-token-123')
      expect(store.token).toBe('test-token-123')

      store.setRefreshToken('refresh-token-456')
      expect(store.refreshToken).toBe('refresh-token-456')

      store.clearToken()
      expect(store.token).toBe('')
      expect(store.refreshToken).toBe('')
    })
  })

  describe('setAuthRole', () => {
    it('设置后台权限角色信息', () => {
      const mockRole: AuthRole = { authRoleId: 1, authRoleName: '管理员', roleKey: 'admin' }
      store.setAuthRole(mockRole)
      expect(store.authRole).toEqual(mockRole)
    })
  })

  describe('WebSocket 配置', () => {
    it('更新 WebSocket 配置', () => {
      store.updateWsConfig({ deviceName: 'test-device' })
      expect(store.wsConfig.deviceName).toBe('test-device')
      // url 应保留默认值
      expect(store.wsConfig.url).toBeTruthy()
    })
  })
})
