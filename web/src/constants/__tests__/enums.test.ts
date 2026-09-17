import { describe, expect, it } from 'vitest'

import * as enums from '../enums'

const { DeviceState } = enums

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

describe('登记范围', () => {
  // 主题、语言、上传状态这类纯前端概念各自的 composable 已经定义了类型，
  // 在这里再登记一份只会两边漂移（历史上 enums.MessageType 就与后端的 messageType 取值对不上）。
  // UserState / UserType / ConfigType 曾在这里登记过，因为代码里从未真正引用而删除；
  // ConfigType 的联合类型定义仍在 types/config.ts，那里才是唯一真源。
  it('只登记后端下发且被引用的枚举', () => {
    expect(Object.keys(enums).sort()).toEqual(['DeviceState'])
  })
})
