import type { Rule } from 'ant-design-vue/es/form'
import type { Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  VALIDATION_RULES,
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  USERNAME_MAX_LENGTH,
  USERNAME_MIN_LENGTH,
} from '@/constants/api'

/** 姓名长度上限，真源 UserRegisterReq.name 的 @Size(max = 50) */
const NAME_MAX_LENGTH = 50
/** 姓名长度下限，前端附加要求，后端无约束 */
const NAME_MIN_LENGTH = 2

// 规则统一 blur + change 触发：只在失焦时校验会让用户改完输入仍看到上一次的旧错误
export function useFormValidation() {
  const { t } = useI18n()

  return {
    // 邮箱验证规则
    emailRules: [
      { required: true, message: t('validation.enterEmail'), trigger: ['blur', 'change'] },
      { type: 'email', message: t('validation.enterValidEmail'), trigger: ['blur', 'change'] },
    ] as Rule[],

    // 用户名验证规则
    usernameRules: [
      { required: true, message: t('validation.enterUsername'), trigger: ['blur', 'change'] },
      {
        min: USERNAME_MIN_LENGTH,
        max: USERNAME_MAX_LENGTH,
        message: t('validation.usernameLength', { min: USERNAME_MIN_LENGTH, max: USERNAME_MAX_LENGTH }),
        trigger: ['blur', 'change'],
      },
      {
        pattern: /^[a-zA-Z0-9_]+$/,
        message: t('validation.username'),
        trigger: ['blur', 'change'],
      },
    ] as Rule[],

    // 密码验证规则
    passwordRules: [
      { required: true, message: t('validation.enterPassword'), trigger: ['blur', 'change'] },
      {
        min: PASSWORD_MIN_LENGTH,
        max: PASSWORD_MAX_LENGTH,
        message: t('validation.passwordLength', { min: PASSWORD_MIN_LENGTH, max: PASSWORD_MAX_LENGTH }),
        trigger: ['blur', 'change'],
      },
    ] as Rule[],

    // 确认密码验证规则（响应式版本）
    confirmPasswordRules: (passwordRef: Ref<string>): Rule[] => [
      {
        validator: (_rule: Rule, value: string) => {
          if (!value) {
            return Promise.reject(t('validation.enterConfirmPassword'))
          }
          if (value !== passwordRef.value) {
            return Promise.reject(t('validation.confirmPassword'))
          }
          return Promise.resolve()
        },
        trigger: ['blur', 'change'],
      },
    ],

    // 验证码规则
    verificationCodeRules: [
      { required: true, message: t('validation.enterVerificationCode'), trigger: ['blur', 'change'] },
      { len: 6, message: t('validation.verificationCodeLength', { length: 6 }), trigger: ['blur', 'change'] },
    ] as Rule[],

    // 手机号规则（可选）
    telRules: [
      {
        pattern: VALIDATION_RULES.PHONE_PATTERN,
        message: t('validation.enterValidPhone'),
        trigger: ['blur', 'change'],
      },
    ] as Rule[],

    // 姓名规则
    nameRules: [
      { required: true, message: t('validation.enterName'), trigger: ['blur', 'change'] },
      {
        min: NAME_MIN_LENGTH,
        max: NAME_MAX_LENGTH,
        message: t('validation.nameLength', { min: NAME_MIN_LENGTH, max: NAME_MAX_LENGTH }),
        trigger: ['blur', 'change'],
      },
    ] as Rule[],
  }
}
