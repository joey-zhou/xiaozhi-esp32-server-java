import type { Rule } from 'ant-design-vue/es/form'
import type { Ref } from 'vue'
import { useI18n } from 'vue-i18n'

// 创建验证规则的工厂函数
export function createValidationRules() {
  const { t } = useI18n()

  return {
    // 邮箱验证规则
    emailRules: [
      { required: true, message: t('validation.enterEmail'), trigger: 'blur' },
      { type: 'email', message: t('validation.enterValidEmail'), trigger: 'blur' },
    ] as Rule[],

    // 用户名验证规则
    usernameRules: [
      { required: true, message: t('validation.enterUsername'), trigger: 'blur' },
      { min: 3, max: 20, message: t('validation.usernameLength', { min: 3, max: 20 }), trigger: 'blur' },
      {
        pattern: /^[a-zA-Z0-9_]+$/,
        message: t('validation.username'),
        trigger: 'blur',
      },
    ] as Rule[],

    // 密码验证规则
    passwordRules: [
      { required: true, message: t('validation.enterPassword'), trigger: 'blur' },
      { min: 6, max: 20, message: t('validation.passwordLength', { min: 6, max: 20 }), trigger: 'blur' },
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
        trigger: 'blur',
      },
    ],

    // 验证码规则
    verificationCodeRules: [
      { required: true, message: t('validation.enterVerificationCode'), trigger: 'blur' },
      { len: 6, message: t('validation.verificationCodeLength', { length: 6 }), trigger: 'blur' },
    ] as Rule[],

    // 手机号规则（可选）
    telRules: [
      {
        pattern: /^1[3-9]\d{9}$/,
        message: t('validation.enterValidPhone'),
        trigger: 'blur',
      },
    ] as Rule[],

    // 姓名规则
    nameRules: [
      { required: true, message: t('validation.enterName'), trigger: 'blur' },
      { min: 2, max: 20, message: t('validation.nameLength', { min: 2, max: 20 }), trigger: 'blur' },
    ] as Rule[],
  }
}

// 主要的 composable 函数
export function useFormValidation() {
  return createValidationRules()
}

// 为了向后兼容，提供延迟初始化的规则
// 这些函数需要在 setup 上下文中调用
export function useEmailRules() {
  return createValidationRules().emailRules
}

export function useUsernameRules() {
  return createValidationRules().usernameRules
}

export function usePasswordRules() {
  return createValidationRules().passwordRules
}

export function useVerificationCodeRules() {
  return createValidationRules().verificationCodeRules
}

export function useTelRules() {
  return createValidationRules().telRules
}

export function useNameRules() {
  return createValidationRules().nameRules
}

export function useConfirmPasswordRules(passwordRef: Ref<string>) {
  return createValidationRules().confirmPasswordRules(passwordRef)
}