import { describe, expect, it } from 'vitest'

import * as enums from '../enums'

const { ConfigType, DeviceState, UserState, UserType } = enums

describe('DeviceState', () => {
  // 真源 xiaozhi-common/.../model/bo/DeviceBO.java:11-15
  // 与 V1__init.sql sys_device.state enum('0','1','2')
  it('是字符串枚举，含待机态', () => {
    expect(DeviceState.OFFLINE).toBe('0')
    expect(DeviceState.ONLINE).toBe('1')
    expect(DeviceState.STANDBY).toBe('2')
  })

  it('可直接与 types/device.ts 里 string 型的 state 比较', () => {
    const state: string = '1'
    expect(state === DeviceState.ONLINE).toBe(true)
  })
})

describe('UserState / UserType', () => {
  // 真源 UserResp.java:34,37 均为 String，与 V1__init.sql sys_user.state/isAdmin enum('1','0')
  it('是字符串枚举，可与 types/user.ts 里 string 型的字段比较', () => {
    expect(UserState.NORMAL).toBe('1')
    expect(UserState.DISABLED).toBe('0')
    expect(UserType.ADMIN).toBe('1')
    expect(UserType.NORMAL).toBe('0')

    const state: string = '1'
    expect(state === UserState.NORMAL).toBe(true)
  })
})

describe('ConfigType', () => {
  // 真源 web/src/types/config.ts:4 的 ConfigType 联合类型
  it('覆盖联合类型的全部 6 个取值', () => {
    expect(Object.values(ConfigType).sort()).toEqual([
      'agent',
      'llm',
      'oss',
      'stt',
      'tts',
      'voice_clone',
    ])
  })
})

describe('登记范围', () => {
  // 主题、语言、上传状态这类纯前端概念各自的 composable 已经定义了类型，
  // 在这里再登记一份只会两边漂移（历史上 enums.MessageType 就与后端的 messageType 取值对不上）
  it('只登记后端下发的枚举', () => {
    expect(Object.keys(enums).sort()).toEqual(['ConfigType', 'DeviceState', 'UserState', 'UserType'])
  })
})
