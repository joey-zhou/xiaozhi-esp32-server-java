<script setup lang="ts">
import { reactive, ref, toRef, computed } from 'vue'
import type { FormInstance } from 'ant-design-vue'
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
} = useVerificationCode()
const {
  passwordRules,
  emailRules,
  verificationCodeRules,
  confirmPasswordRules,
} = useFormValidation()

const formRef = ref<FormInstance>()
const showVerificationInput = ref(false)

// 表单数据
const formData = reactive({
  email: '',
  verificationCode: '',
  newPassword: '',
  confirmPassword: '',
})

const rules = computed<Record<string, import('ant-design-vue/es/form').Rule[]>>(() => ({
  email: emailRules,
  verificationCode: verificationCodeRules,
  newPassword: passwordRules,
  confirmPassword: confirmPasswordRules(toRef(formData, 'newPassword')),
}))

const handleSendCode = async () => {
  try {
    await formRef.value?.validateFields(['email'])
    const success = await sendForgetCode(formData.email)
    if (success) {
      showVerificationInput.value = true
    }
  } catch {
    // 表单校验未通过时不发送验证码
  }
}

// 验证码由后端 resetPassword 一次性校验并消费，前端不做前置校验
const handleSubmit = async () => {
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
              >
                <template #prefix>
                  <MailOutlined />
                </template>
                <template #suffix>
                  <button
                    type="button"
                    class="send-code-btn"
                    :class="{
                      disabled: !canSendCode,
                      loading: sendCodeLoading,
                    }"
                    :disabled="!canSendCode"
                    @click="handleSendCode"
                  >
                    <LoadingOutlined v-if="sendCodeLoading" />
                    {{ buttonText }}
                  </button>
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
              >
                <template #prefix>
                  <SafetyCertificateOutlined />
                </template>
              </a-input>
            </a-form-item>

            <!-- 新密码输入 -->
            <a-form-item :label="t('auth.newPassword')" name="newPassword" v-if="showVerificationInput">
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
            <a-form-item :label="t('auth.confirmNewPassword')" name="confirmPassword" v-if="showVerificationInput">
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
                :disabled="!showVerificationInput"
                block
                size="large"
                class="forget-button"
              >
                {{ t('auth.resetPassword') }}
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
@use '../styles/auth-shared' as *;

// 主容器
.forget-container {
  @include auth-page;
}

// 背景图片
.earth-background {
  @include auth-background;
}

// 忘记密码卡片
.forget-card {
  @include auth-card;
}

// 标题样式
.welcome-title {
  @include auth-title;
}

// 输入框样式
.input-field {
  @include auth-input;
}

// 表单标签
/* 这三页压在一张固定的深色背景图上，标签色不跟随明暗主题，保持浅灰即可 */
:deep(.ant-form-item-label > label) {
  @include auth-form-label;
}

// 发送验证码按钮
.send-code-btn {
  @include auth-send-code-base;

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
  @include auth-hint-text;
}

.back-link {
  @include auth-link;
}
</style>
