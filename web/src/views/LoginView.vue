<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { useI18n } from 'vue-i18n'
import {
  UserOutlined,
  LockOutlined,
  WechatOutlined,
  QqOutlined,
  MobileOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons-vue'
import { useAuth } from '@/composables/useAuth'
import { useFormValidation } from '@/composables/useFormValidation'

const { t } = useI18n()
const { loading, sendCodeLoading, countdown, login, telLogin, sendVerificationCode, getRememberedCredentials } = useAuth()
const { usernameRules, passwordRules } = useFormValidation()

const loginType = ref<'account' | 'mobile'>('account')
const formRef = ref<FormInstance>()
const formState = reactive({
  username: '',
  password: '',
  rememberMe: false,
})

const mobileFormState = reactive({
  tel: '',
  code: '',
})

const rules: Record<string, Rule[]> = {
  username: usernameRules,
  password: passwordRules,
}

// 切换登录方式
const switchLoginType = (type: 'account' | 'mobile') => {
  loginType.value = type
}

// 发送验证码
const handleSendCode = () => {
  sendVerificationCode(mobileFormState.tel)
}

// 提交表单
const handleSubmit = async () => {
  if (loginType.value === 'account') {
    await login(formState)
  } else {
    await telLogin(mobileFormState)
  }
}

onMounted(() => {
  const credentials = getRememberedCredentials()
  Object.assign(formState, credentials)
})
</script>

<template>
  <div class="login-container">
    <div class="earth-background"></div>

    <a-row type="flex" justify="center" align="middle" style="min-height: 100vh">
      <a-col :xs="22" :sm="14" :md="12" :lg="10" :xl="8">
        <a-card class="login-card" :bordered="false">
          <div class="welcome-title">{{ t('auth.login') }}</div>

          <a-form
            ref="formRef"
            :model="loginType === 'account' ? formState : mobileFormState"
            :rules="rules"
            layout="vertical"
            @finish="handleSubmit"
            :hideRequiredMark="true"
          >
            <!-- 账号密码登录 -->
            <template v-if="loginType === 'account'">
              <a-form-item :label="t('user.username')" name="username">
                <a-input
                  v-model:value="formState.username"
                  :placeholder="t('common.enterUsername')"
                  size="large"
                  class="input-field"
                >
                  <template #prefix>
                    <UserOutlined />
                  </template>
                </a-input>
              </a-form-item>

              <a-form-item :label="t('account.password')" name="password">
                <a-input-password
                  v-model:value="formState.password"
                  :placeholder="t('common.enterPassword')"
                  size="large"
                  class="input-field"
                >
                  <template #prefix>
                    <LockOutlined />
                  </template>
                </a-input-password>
              </a-form-item>

              <a-row type="flex" justify="space-between" align="middle" class="form-options">
                <a-col>
                  <a-checkbox v-model:checked="formState.rememberMe"> {{ t('auth.rememberMe') }} </a-checkbox>
                </a-col>
                <a-col>
                  <router-link to="/forget"> {{ t('auth.forgetPassword') }} </router-link>
                </a-col>
              </a-row>
            </template>

            <!-- 手机号验证码登录 -->
            <template v-else>
              <a-form-item :label="t('auth.mobilePhone')" name="tel">
                <a-input
                  v-model:value="mobileFormState.tel"
                  :placeholder="t('auth.enterMobilePhone')"
                  size="large"
                  class="input-field"
                >
                  <template #prefix>
                    <MobileOutlined />
                  </template>
                </a-input>
              </a-form-item>

              <a-form-item :label="t('auth.verificationCode')" name="code">
                <a-input
                  v-model:value="mobileFormState.code"
                  :placeholder="t('common.enterVerificationCode')"
                  size="large"
                  class="input-field"
                >
                  <template #prefix>
                    <SafetyCertificateOutlined />
                  </template>
                  <template #suffix>
                    <span
                      class="send-code-btn"
                      :class="{
                        disabled: !mobileFormState.tel || sendCodeLoading || countdown > 0,
                        loading: sendCodeLoading,
                      }"
                      @click="handleSendCode"
                    >
                      {{
                        sendCodeLoading
                          ? t('auth.sending')
                          : countdown > 0
                            ? t('auth.resendAfter', { seconds: countdown })
                            : t('auth.sendVerificationCode')
                      }}
                    </span>
                  </template>
                </a-input>
              </a-form-item>

              <a-row type="flex" justify="end" align="middle" class="form-options">
                <a-col>
                  <a @click="switchLoginType('account')" class="switch-login-type">
                    {{ t('auth.useAccountLogin') }}
                  </a>
                </a-col>
              </a-row>
            </template>

            <a-form-item style="margin-top: 24px">
              <a-button
                type="primary"
                html-type="submit"
                :loading="loading"
                block
                size="large"
                class="login-button"
              >
                {{ t('auth.login') }}
              </a-button>
            </a-form-item>

            <div class="privacy-terms">
              <span class="terms-text">{{ t('auth.loginAgreement') }}</span>
              <a href="#" class="terms-link">《隐私协议》</a>
              <span class="terms-text">和</span>
              <a href="#" class="terms-link">《服务条款》</a>
            </div>

            <a-divider>
              <span class="divider-text">{{ t('auth.otherLoginMethods') }}</span>
            </a-divider>

            <a-row type="flex" justify="center" :gutter="20" style="margin-bottom: 24px">
              <a-col>
                <a-button type="default" shape="circle" size="large" class="social-button wechat">
                  <WechatOutlined />
                </a-button>
              </a-col>
              <a-col>
                <a-button type="default" shape="circle" size="large" class="social-button qq">
                  <QqOutlined />
                </a-button>
              </a-col>
              <a-col>
                <a-button
                  type="default"
                  shape="circle"
                  size="large"
                  class="social-button mobile"
                  @click="switchLoginType('mobile')"
                >
                  <MobileOutlined />
                </a-button>
              </a-col>
            </a-row>

            <div class="register-wrapper">
              <span class="register-text">{{ t('auth.noAccount') }}</span>
              <router-link to="/register" class="register-link"> {{ t('auth.register') }} </router-link>
            </div>
          </a-form>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<style lang="scss" scoped>
.login-container {
  position: relative;
  min-height: 100vh;
  max-width: 1280px;
  margin: 0 auto;
  font-family: 'Segoe UI', 'Microsoft YaHei', sans-serif;
}

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

.login-card {
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

.welcome-title {
  text-align: center;
  color: #ffffff;
  font-size: 28px;
  font-weight: 600;
  margin-bottom: 32px;
  letter-spacing: 1px;
}

.input-field {
  background: rgba(255, 255, 255, 0.1) !important;
  border: none !important;

  &:hover {
    background: rgba(255, 255, 255, 0.13) !important;
  }

  &:focus,
  &.ant-input-focused {
    background: #888888 !important;
    box-shadow: 0 0 0 2px rgba(66, 133, 244, 0.2) !important;
  }

  :deep(input) {
    background: transparent !important;
    color: #ffffff !important;

    &::placeholder {
      color: rgba(255, 255, 255, 0.7) !important;
    }
  }

  :deep(.ant-input-prefix),
  :deep(.ant-input-password-icon) {
    color: rgba(255, 255, 255, 0.6) !important;
  }

  :deep(.ant-input-affix-wrapper) {
    background: transparent !important;
    border: none !important;
  }
}

:deep(.ant-form-item-label > label) {
  color: #cccccc !important;
  font-weight: 500;
}

.form-options {
  margin-bottom: 20px;

  :deep(.ant-checkbox-wrapper) {
    color: rgba(255, 255, 255, 0.8);
  }

  :deep(.ant-checkbox-inner) {
    background-color: rgba(255, 255, 255, 0.1) !important;
    border-color: rgba(255, 255, 255, 0.3) !important;
  }

  :deep(.ant-checkbox-checked .ant-checkbox-inner) {
    background-color: #4285f4 !important;
    border-color: #4285f4 !important;
  }

  a {
    color: #4285f4;

    &:hover {
      color: #3367d6;
    }
  }
}

.login-button {
  transition: all 0.3s ease !important;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 20px rgba(66, 133, 244, 0.3) !important;
  }
}

.privacy-terms {
  text-align: center;
  margin: 16px 0;
  font-size: 12px;
}

.terms-text {
  color: rgba(255, 255, 255, 0.6);
  margin: 0 2px;
}

.terms-link {
  color: #4285f4;
  text-decoration: none;

  &:hover {
    color: #3367d6;
  }
}

.register-wrapper {
  text-align: center;
  font-size: 14px;
}

.register-text {
  color: rgba(255, 255, 255, 0.6);
  margin-right: 8px;
}

.register-link {
  color: #4285f4;
  text-decoration: none;

  &:hover {
    color: #3367d6;
  }
}

.social-button {
  background: rgba(255, 255, 255, 0.1) !important;
  border: none !important;
  color: #ffffff !important;
  transition: all 0.3s ease;
  box-shadow: none;

  &:hover {
    transform: translateY(-2px);
  }

  &.wechat:hover {
    background: rgba(9, 187, 7, 0.2) !important;
    color: #09bb07 !important;
  }

  &.qq:hover {
    background: rgba(18, 183, 245, 0.2) !important;
    color: #12b7f5 !important;
  }

  &.mobile:hover {
    background: rgba(255, 140, 0, 0.2) !important;
    color: #ff8c00 !important;
  }
}

:deep(.ant-divider-inner-text) {
  color: rgba(255, 255, 255, 0.6) !important;
}

.divider-text {
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
}

:deep(.ant-divider-horizontal.ant-divider-with-text::before),
:deep(.ant-divider-horizontal.ant-divider-with-text::after) {
  border-top-color: rgba(255, 255, 255, 0.2) !important;
}

// 发送验证码按钮
.send-code-btn {
  color: #4285f4;
  cursor: pointer;
  font-size: 12px;
  white-space: nowrap;
  user-select: none;
  transition: all 0.3s;

  &:hover:not(.disabled) {
    color: #3367d6;
  }

  &.disabled {
    color: rgba(255, 255, 255, 0.4);
    cursor: not-allowed;
  }

  &.loading {
    cursor: not-allowed;
  }
}

// 切换登录方式链接
.switch-login-type {
  color: #4285f4;
  cursor: pointer;
  font-size: 14px;

  &:hover {
    color: #3367d6;
  }
}
</style>