import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import type { Rule } from 'ant-design-vue/es/form'
import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  USERNAME_MAX_LENGTH,
  USERNAME_MIN_LENGTH,
  VALIDATION_RULES,
} from '@/constants/api'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

import { useFormValidation } from '../useFormValidation'

/** 取规则数组里带长度约束的那一条 */
function lengthRule(rules: Rule[]): Rule {
  const rule = rules.find((item) => 'min' in item || 'max' in item)
  expect(rule).toBeDefined()
  return rule as Rule
}

describe('useFormValidation 长度阈值', () => {
  it('用户名与密码的阈值取自 constants/api.ts', () => {
    const { usernameRules, passwordRules } = useFormValidation()

    expect(lengthRule(usernameRules)).toMatchObject({
      min: USERNAME_MIN_LENGTH,
      max: USERNAME_MAX_LENGTH,
    })
    expect(lengthRule(passwordRules)).toMatchObject({
      min: PASSWORD_MIN_LENGTH,
      max: PASSWORD_MAX_LENGTH,
    })
  })

  // 真源 UserRegisterReq.name 的 @Size(max = 50)，早先前端写成 20 会先于后端拦下合法输入
  it('姓名上限与后端 @Size(max = 50) 一致', () => {
    const { nameRules } = useFormValidation()
    expect(lengthRule(nameRules)).toMatchObject({ max: 50 })
  })

  it('手机号规则复用 constants 里的 PHONE_PATTERN', () => {
    const { telRules } = useFormValidation()
    expect(telRules[0]).toMatchObject({ pattern: VALIDATION_RULES.PHONE_PATTERN })
  })
})

describe('useFormValidation 触发时机', () => {
  // 只在 blur 校验会让用户改完输入仍看到上一次的旧错误
  it('所有规则都同时在 blur 和 change 触发', () => {
    const { confirmPasswordRules, ...staticRules } = useFormValidation()
    const allRules: Rule[] = [
      ...Object.values(staticRules).flat(),
      ...confirmPasswordRules(ref('secret')),
    ]

    expect(allRules.length).toBeGreaterThan(0)
    for (const rule of allRules) {
      expect(rule.trigger).toEqual(['blur', 'change'])
    }
  })
})
