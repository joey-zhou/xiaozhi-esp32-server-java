<script setup lang="ts">
import { reactive, ref, toRef } from 'vue'
import type { FormInstance } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import {
  UserOutlined,
  IdcardOutlined,
  MailOutlined,
  PhoneOutlined,
  SafetyCertificateOutlined,
  LockOutlined,
  LoadingOutlined,
} from '@ant-design/icons-vue'
import { useAuth } from '@/composables/useAuth'
import { useVerificationCode } from '@/composables/useVerificationCode'
import { useFormValidation } from '@/composables/useFormValidation'

const { t } = useI18n()
const { loading, register } = useAuth()
const {
  sendCodeLoading,
  countdown,
  canSendCode,
  buttonText,
  sendRegisterCode,
} = useVerificationCode()
const {
  usernameRules,
  passwordRules,
  nameRules,
  emailRules,
  telRules,
  verificationCodeRules,
  confirmPasswordRules,
} = useFormValidation()

const formRef = ref<FormInstance>()
const showVerificationInput = ref(false)

// 表单数据
const formData = reactive({
  name: '',
  username: '',
  email: '',
  tel: '',
  password: '',
  confirmPassword: '',
  verifyCode: '',
  agreeTerms: false,
})

const rules = {
  name: nameRules,
  username: usernameRules,
  email: emailRules,
  tel: telRules,
  password: passwordRules,
  confirmPassword: confirmPasswordRules(toRef(formData, 'password')),
  verifyCode: verificationCodeRules,
}

const handleSendCode = async () => {
  try {
    await formRef.value?.validateFields(['email', 'username'])
    const success = await sendRegisterCode(formData.email, formData.username)
    if (success) {
      showVerificationInput.value = true
    }
  } catch (error) {
    // Validation failed
  }
}

const handleSubmit = async () => {
  await register({
    name: formData.name,
    username: formData.username,
    email: formData.email,
    tel: formData.tel,
    password: formData.password,
    confirmPassword: formData.confirmPassword,
    verifyCode: formData.verifyCode,
    agreeTerms: formData.agreeTerms,
  })
}
</script>

<template>
  <div class="register-container">
    <!-- 地球背景 -->
    <div class="earth-background"></div>

    <!-- 注册区域 -->
    <a-row type="flex" justify="center" align="middle" style="min-height: 100vh">
      <a-col :xs="22" :sm="14" :md="12" :lg="10" :xl="8">
        <a-card class="register-card" :bordered="false">
          <!-- 标题 -->
          <div class="welcome-title">{{ t('auth.register') }}</div>

          <!-- 注册表单 -->
          <a-form
            ref="formRef"
            :model="formData"
            :rules="rules"
            @finish="handleSubmit"
            :hideRequiredMark="true"
            layout="vertical"
          >
            <!-- 姓名输入 -->
            <a-form-item :label="t('auth.name')" name="name">
              <a-input
                v-model:value="formData.name"
                :placeholder="t('auth.enterName')"
                size="large"
                class="input-field"
              >
                <template #prefix>
                  <UserOutlined />
                </template>
              </a-input>
            </a-form-item>

            <!-- 账号输入 -->
            <a-form-item :label="t('user.username')" name="username">
              <a-input
                v-model:value="formData.username"
                :placeholder="t('common.enterUsername')"
                size="large"
                class="input-field"
              >
                <template #prefix>
                  <IdcardOutlined />
                </template>
              </a-input>
            </a-form-item>

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
                  <span
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

            <!-- 电话输入 -->
            <a-form-item :label="t('user.phone')" name="tel">
              <a-input
                v-model:value="formData.tel"
                :placeholder="t('auth.enterPhoneOptional')"
                size="large"
                class="input-field"
              >
                <template #prefix>
                  <PhoneOutlined />
                </template>
              </a-input>
            </a-form-item>

            <!-- 验证码输入 - 发送成功后才显示 -->
            <a-form-item :label="t('auth.emailVerificationCode')" name="verifyCode" v-if="showVerificationInput">
              <a-input
                v-model:value="formData.verifyCode"
                :placeholder="t('common.enterVerificationCode')"
                size="large"
                class="input-field"
              >
                <template #prefix>
                  <SafetyCertificateOutlined />
                </template>
              </a-input>
            </a-form-item>

            <!-- 密码输入 -->
            <a-form-item :label="t('account.password')" name="password">
              <a-input-password
                v-model:value="formData.password"
                :placeholder="t('common.enterPassword')"
                size="large"
                class="input-field"
              >
                <template #prefix>
                  <LockOutlined />
                </template>
              </a-input-password>
            </a-form-item>

            <!-- 确认密码输入 -->
            <a-form-item :label="t('account.confirmPassword')" name="confirmPassword">
              <a-input-password
                v-model:value="formData.confirmPassword"
                :placeholder="t('common.enterConfirmPassword')"
                size="large"
                class="input-field"
              >
                <template #prefix>
                  <LockOutlined />
                </template>
              </a-input-password>
            </a-form-item>

            <!-- 用户协议 -->
            <a-form-item name="agreeTerms" :rules="[{ required: true, message: t('auth.agreeTermsRequired') }]">
              <a-checkbox v-model:checked="formData.agreeTerms">
                {{ t('auth.agreeTerms') }}
                <a href="#" class="terms-link">《用户协议》</a>
                和
                <a href="#" class="terms-link">《隐私政策》</a>
              </a-checkbox>
            </a-form-item>

            <!-- 注册按钮 -->
            <a-form-item style="margin-top: 24px">
              <a-button
                type="primary"
                html-type="submit"
                :loading="loading"
                block
                size="large"
                class="register-button"
              >
                {{ t('auth.register') }}
              </a-button>
            </a-form-item>

            <!-- 登录链接 -->
            <div class="login-wrapper">
              <span class="login-text">{{ t('auth.haveAccount') }}</span>
              <router-link to="/login" class="login-link"> {{ t('auth.loginNow') }} </router-link>
            </div>
          </a-form>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<style lang="scss" scoped>
// 主容器
.register-container {
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

// 注册卡片
.register-card {
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

// 用户协议
:deep(.ant-checkbox-wrapper) {
  color: rgba(255, 255, 255, 0.8);
  font-size: 12px;
  line-height: 1.4;
}

:deep(.ant-checkbox-inner) {
  background-color: rgba(255, 255, 255, 0.1) !important;
  border-color: rgba(255, 255, 255, 0.3) !important;
}

:deep(.ant-checkbox-checked .ant-checkbox-inner) {
  background-color: #4285f4 !important;
  border-color: #4285f4 !important;
}

.terms-link {
  color: #4285f4;
  text-decoration: none;
  font-size: 12px;

  &:hover {
    color: #3367d6;
  }
}

// 注册按钮
.register-button {
  height: 36px !important;
  transition: all 0.3s ease !important;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 20px rgba(66, 133, 244, 0.3) !important;
  }
}

// 登录链接
.login-wrapper {
  text-align: center;
  font-size: 14px;
  margin-top: 16px;
}

.login-text {
  color: rgba(255, 255, 255, 0.6);
  margin-right: 8px;
}

.login-link {
  color: #4285f4;
  text-decoration: none;

  &:hover {
    color: #3367d6;
  }
}
</style>