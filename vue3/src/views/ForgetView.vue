<script setup lang="ts">
import { reactive, ref, toRef, computed } from 'vue'
import type { FormInstance } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import {
  MailOutlined,
  SafetyCertificateOutlined,
  LockOutlined,
  LoadingOutlined,
} from '@ant-design/icons-vue'
import { useAuth } from '@/composables/useAuth'
import { useVerificationCode } from '@/composables/useVerificationCode'
import { useFormValidation } from '@/composables/useFormValidation'

const { t } = useI18n()
const { loading, resetPassword } = useAuth()
const {
  sendCodeLoading,
  canSendCode,
  buttonText,
  sendForgetCode,
  verifyCode,
} = useVerificationCode()
const {
  passwordRules,
  emailRules,
  verificationCodeRules,
  confirmPasswordRules,
} = useFormValidation()

const formRef = ref<FormInstance>()
const isEmailVerified = ref(false)
const showVerificationInput = ref(false)

// 表单数据
const formData = reactive({
  email: '',
  verificationCode: '',
  newPassword: '',
  confirmPassword: '',
})

const rules = computed(() => {
  const baseRules: any = {
    email: emailRules,
    verificationCode: verificationCodeRules,
  }

  if (isEmailVerified.value) {
    baseRules.newPassword = passwordRules
    baseRules.confirmPassword = confirmPasswordRules(toRef(formData, 'newPassword'))
  }

  return baseRules
})

const handleSendCode = async () => {
  try {
    await formRef.value?.validateFields(['email'])
    const success = await sendForgetCode(formData.email)
    if (success) {
      showVerificationInput.value = true
    }
  } catch (error) {
    // Validation failed
  }
}

const handleSubmit = async () => {
  if (!isEmailVerified.value) {
    await handleVerifyEmail()
  } else {
    await handleResetPassword()
  }
}

const handleVerifyEmail = async () => {
  loading.value = true
  try {
    const success = await verifyCode(formData.email, formData.verificationCode, 'forget')
    if (success) {
      isEmailVerified.value = true
      message.success('验证成功！请设置新密码')
    }
  } finally {
    loading.value = false
  }
}

const handleResetPassword = async () => {
  await resetPassword({
    email: formData.email,
    verificationCode: formData.verificationCode,
    newPassword: formData.newPassword,
    confirmPassword: formData.confirmPassword,
  })
}
</script>

<template>
  <div class="forget-container">
    <!-- 地球背景 -->
    <div class="earth-background"></div>

    <!-- 忘记密码区域 -->
    <a-row type="flex" justify="center" align="middle" style="min-height: 100vh">
      <a-col :xs="22" :sm="14" :md="12" :lg="10" :xl="8">
        <a-card class="forget-card" :bordered="false">
          <!-- 标题 -->
          <div class="welcome-title">{{ t('auth.forgetPassword') }}</div>

          <!-- 忘记密码表单 -->
          <a-form
            ref="formRef"
            :model="formData"
            :rules="rules"
            @finish="handleSubmit"
            :hideRequiredMark="true"
            layout="vertical"
          >
            <!-- 邮箱输入 -->
            <a-form-item :label="t('user.email')" name="email">
              <a-input
                v-model:value="formData.email"
                :placeholder="t('common.enterEmail')"
                size="large"
                class="input-field"
                :disabled="isEmailVerified"
              >
                <template #prefix>
                  <MailOutlined />
                </template>
                <template #suffix>
                  <span
                    v-if="!isEmailVerified"
                    class="send-code-btn"
                    :class="{
                      disabled: !canSendCode,
                      loading: sendCodeLoading,
                    }"
                    @click="handleSendCode"
                  >
                    <LoadingOutlined v-if="sendCodeLoading" />
                    {{ buttonText }}
                  </span>
                </template>
              </a-input>
            </a-form-item>

            <!-- 验证码输入 - 发送成功后才显示 -->
            <a-form-item :label="t('auth.emailVerificationCode')" name="verificationCode" v-if="showVerificationInput">
              <a-input
                v-model:value="formData.verificationCode"
                :placeholder="t('common.enterVerificationCode')"
                size="large"
                class="input-field"
                :disabled="isEmailVerified"
              >
                <template #prefix>
                  <SafetyCertificateOutlined />
                </template>
              </a-input>
            </a-form-item>

            <!-- 新密码输入 -->
            <a-form-item :label="t('auth.newPassword')" name="newPassword" v-if="isEmailVerified">
              <a-input-password
                v-model:value="formData.newPassword"
                :placeholder="t('auth.enterNewPassword')"
                size="large"
                class="input-field"
              >
                <template #prefix>
                  <LockOutlined />
                </template>
              </a-input-password>
            </a-form-item>

            <!-- 确认新密码输入 -->
            <a-form-item :label="t('auth.confirmNewPassword')" name="confirmPassword" v-if="isEmailVerified">
              <a-input-password
                v-model:value="formData.confirmPassword"
                :placeholder="t('auth.enterConfirmNewPassword')"
                size="large"
                class="input-field"
              >
                <template #prefix>
                  <LockOutlined />
                </template>
              </a-input-password>
            </a-form-item>

            <!-- 提交按钮 -->
            <a-form-item style="margin-top: 24px">
              <a-button
                type="primary"
                html-type="submit"
                :loading="loading"
                block
                size="large"
                class="forget-button"
              >
                {{ isEmailVerified ? t('auth.resetPassword') : t('auth.verifyEmail') }}
              </a-button>
            </a-form-item>

            <!-- 返回登录链接 -->
            <div class="back-login-wrapper">
              <span class="back-text">{{ t('auth.rememberPassword') }}</span>
              <router-link to="/login" class="back-link"> {{ t('auth.backToLogin') }} </router-link>
            </div>
          </a-form>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<style lang="scss" scoped>
// 主容器
.forget-container {
  position: relative;
  min-height: 100vh;
  max-width: 1280px;
  margin: 0 auto;
  font-family: 'Segoe UI', 'Microsoft YaHei', sans-serif;
}

// 背景图片
.earth-background {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: url('/static/img/galaxy.jpg') center center;
  background-size: cover;
  background-repeat: no-repeat;
  z-index: 0;

  &::after {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.3);
    z-index: 1;
  }
}

// 忘记密码卡片
.forget-card {
  background: rgba(42, 42, 42, 0.35) !important;
  backdrop-filter: blur(10px);
  border: none !important;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.5) !important;
  position: relative;
  z-index: 10;

  :deep(.ant-card-body) {
    padding: 40px;
  }
}

// 标题样式
.welcome-title {
  text-align: center;
  color: #ffffff;
  font-size: 28px;
  font-weight: 600;
  margin-bottom: 32px;
  letter-spacing: 1px;
  width: 100%;
  display: block;
}

// 输入框样式
.input-field {
  background: rgba(255, 255, 255, 0.1) !important;
  border: none !important;
  box-shadow: none !important;

  &:hover {
    background: rgba(255, 255, 255, 0.13) !important;
    border: none !important;
    box-shadow: none !important;
  }

  &:focus,
  &.ant-input-focused {
    background: rgba(255, 255, 255, 0.15) !important;
    border: none !important;
    box-shadow: 0 0 0 2px rgba(66, 133, 244, 0.2) !important;
  }

  :deep(input) {
    background: transparent !important;
    color: #ffffff !important;
    border: none !important;
    box-shadow: none !important;

    &::placeholder {
      color: rgba(255, 255, 255, 0.7) !important;
    }

    &:focus {
      border: none !important;
      box-shadow: none !important;
    }
  }

  :deep(.ant-input-prefix) {
    color: rgba(255, 255, 255, 0.6) !important;
  }

  :deep(.ant-input-password-icon) {
    color: rgba(255, 255, 255, 0.6) !important;
  }

  :deep(.ant-input-affix-wrapper) {
    background: transparent !important;
    border: none !important;
    box-shadow: none !important;

    &:hover,
    &:focus,
    &.ant-input-affix-wrapper-focused {
      border: none !important;
      box-shadow: none !important;
    }
  }
}

// 表单标签
:deep(.ant-form-item-label > label) {
  color: #cccccc !important;
  font-weight: 500;
}

// 发送验证码按钮
.send-code-btn {
  color: #4285f4;
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  transition: color 0.3s ease;
  white-space: nowrap;

  &:hover:not(.disabled) {
    color: #3367d6;
  }

  &.disabled {
    color: rgba(255, 255, 255, 0.3);
    cursor: not-allowed;
  }

  &.loading {
    color: #4285f4;
    cursor: default;

    .anticon {
      margin-right: 4px;
    }
  }
}

// 忘记密码按钮
.forget-button {
  height: 36px !important;
  transition: all 0.3s ease !important;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 20px rgba(66, 133, 244, 0.3) !important;
  }
}

// 返回登录链接
.back-login-wrapper {
  text-align: center;
  font-size: 14px;
  margin-top: 16px;
}

.back-text {
  color: rgba(255, 255, 255, 0.6);
  margin-right: 8px;
}

.back-link {
  color: #4285f4;
  text-decoration: none;

  &:hover {
    color: #3367d6;
  }
}
</style>
