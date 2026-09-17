import { describe, expect, it } from 'vitest'

import {
  ALLOWED_AUDIO_TYPES,
  ALLOWED_IMAGE_TYPES,
  DEBOUNCE_DELAY,
  DEFAULT_PAGE_SIZE,
  DESCRIPTION_MAX_LENGTH,
  DEVICE_NAME_MAX_LENGTH,
  MAX_AUDIO_SIZE,
  MAX_IMAGE_SIZE,
  PAGE_SIZE_OPTIONS,
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  REQUEST_TIMEOUT,
  ROLE_NAME_MAX_LENGTH,
  USERNAME_MAX_LENGTH,
  USERNAME_MIN_LENGTH,
  VALIDATION_RULES,
  WS_MAX_RECONNECT_TIMES,
  WS_RECONNECT_DELAY,
} from '../api'

describe('用户名/密码约束对齐后端校验注解', () => {
  // 真源 xiaozhi-common/.../req/UserRegisterReq.java:24 @Size(min = 6, max = 20)
  it('密码长度取 UserRegisterReq.password 的 @Size', () => {
    expect(PASSWORD_MIN_LENGTH).toBe(6)
    expect(PASSWORD_MAX_LENGTH).toBe(20)
  })

  // 真源 xiaozhi-common/.../req/UserRegisterReq.java:19 @Size(min = 3, max = 20)
  it('用户名长度取 UserRegisterReq.username 的 @Size', () => {
    expect(USERNAME_MIN_LENGTH).toBe(3)
    expect(USERNAME_MAX_LENGTH).toBe(20)
  })

  it('用户名正则的长度区间与用户名长度常量一致', () => {
    expect(VALIDATION_RULES.USERNAME_PATTERN.source).toBe(
      `^[a-zA-Z0-9_]{${USERNAME_MIN_LENGTH},${USERNAME_MAX_LENGTH}}$`,
    )
    // 3 位是后端允许的下界，旧值 {4,20} 会把它误判为非法
    expect(VALIDATION_RULES.USERNAME_PATTERN.test('abc')).toBe(true)
    expect(VALIDATION_RULES.USERNAME_PATTERN.test('ab')).toBe(false)
    expect(VALIDATION_RULES.USERNAME_PATTERN.test('a'.repeat(21))).toBe(false)
  })

  it('密码正则不再要求 8 位，与后端 6 位下界一致', () => {
    expect(VALIDATION_RULES.PASSWORD_PATTERN.test('abc123')).toBe(true)
    expect(VALIDATION_RULES.PASSWORD_PATTERN.test('abc12')).toBe(false)
    expect(VALIDATION_RULES.PASSWORD_PATTERN.test(`${'a'.repeat(20)}1`)).toBe(false)
  })

  // 真源 xiaozhi-common/.../req/UserRegisterReq.java:37 @Pattern(regexp = "^1[3-9]\\d{9}$")
  it('手机号正则与 UserRegisterReq.tel 的 @Pattern 一致', () => {
    expect(VALIDATION_RULES.PHONE_PATTERN.source).toBe('^1[3-9]\\d{9}$')
  })
})

describe('库表列长度约束', () => {
  // 真源 xiaozhi-server/src/main/resources/db/migration/V1__init.sql sys_device.deviceName varchar(100)
  it('设备名长度取 sys_device.deviceName 的 varchar 长度', () => {
    expect(DEVICE_NAME_MAX_LENGTH).toBe(100)
  })

  // 真源 V1__init.sql sys_role.roleName varchar(100)
  it('角色名长度取 sys_role.roleName 的 varchar 长度', () => {
    expect(ROLE_NAME_MAX_LENGTH).toBe(100)
  })

  // 真源 V1__init.sql sys_template.templateDesc varchar(500)
  it('描述长度取 varchar(500) 的描述列', () => {
    expect(DESCRIPTION_MAX_LENGTH).toBe(500)
  })
})

describe('分页常量对齐 useTable 现行值', () => {
  it('默认每页条数与 useTable 初值一致', () => {
    expect(DEFAULT_PAGE_SIZE).toBe(10)
  })

  it('每页条数选项是字符串数组，可直接喂给 antd 的 pageSizeOptions', () => {
    expect(PAGE_SIZE_OPTIONS).toEqual(['10', '30', '50', '100', '1000'])
    expect(PAGE_SIZE_OPTIONS.every((option) => typeof option === 'string')).toBe(true)
  })
})

describe('上传大小上限对齐 utils/fileValidators.ts 的四档校验', () => {
  it('图片 2MB / 音频 10MB / 文档 20MB / 固件 50MB', () => {
    expect(MAX_IMAGE_SIZE).toBe(2 * 1024 * 1024)
    expect(MAX_AUDIO_SIZE).toBe(10 * 1024 * 1024)
  })

  it('允许的 MIME 列表保持非空', () => {
    expect(ALLOWED_IMAGE_TYPES.length).toBeGreaterThan(0)
    expect(ALLOWED_AUDIO_TYPES.length).toBeGreaterThan(0)
  })
})

describe('网络相关常量', () => {
  // 真源 web/src/services/request.ts 的 axios timeout
  it('请求超时与 axios 实例一致', () => {
    expect(REQUEST_TIMEOUT).toBe(30000)
  })

  // 真源 web/src/services/websocket.ts 的 reconnectDelay / maxReconnectAttempts
  it('重连延迟与次数与 websocket.ts 一致', () => {
    expect(WS_RECONNECT_DELAY).toBe(2000)
    expect(WS_MAX_RECONNECT_TIMES).toBe(5)
  })

  it('防抖延迟与 useTable.createDebouncedSearch 默认值一致', () => {
    expect(DEBOUNCE_DELAY).toBe(500)
  })
})
