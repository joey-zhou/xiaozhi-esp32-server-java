<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
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
import { useVerificationCode } from '@/composables/useVerificationCode'
import { useFormValidation } from '@/composables/useFormValidation'

const { t } = useI18n()
const { loading, login, telLogin, getRememberedCredentials } = useAuth()
const { sendCodeLoading, canSendCode, buttonText, sendSmsLoginCode } = useVerificationCode()
const { usernameRules, passwordRules, telRules, verificationCodeRules } = useFormValidation()

const loginType = ref<'account' | 'mobile'>('account')
const formState = reactive({
  username: '',
  password: '',
  rememberMe: false,
})

const mobileFormState = reactive({
  tel: '',
  code: '',
})

const accountRules: Record<string, Rule[]> = {
  username: usernameRules,
  password: passwordRules,
}

// telRules 只有格式校验，必填要自己补
const mobileRules: Record<string, Rule[]> = {
  tel: [{ required: true, message: t('auth.enterMobilePhone'), trigger: 'blur' }, ...telRules],
  code: verificationCodeRules,
}

const rules = computed(() => (loginType.value === 'account' ? accountRules : mobileRules))

// 切换登录方式
const switchLoginType = (type: 'account' | 'mobile') => {
  loginType.value = type
}

// 发送验证码
const handleSendCode = () => {
  sendSmsLoginCode(mobileFormState.tel)
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
                    <button
                      type="button"
                      class="send-code-btn"
                      :class="{
                        disabled: !mobileFormState.tel || !canSendCode,
                        loading: sendCodeLoading,
                      }"
                      :disabled="!mobileFormState.tel || !canSendCode"
                      @click="handleSendCode"
                    >
                      {{ buttonText }}
                    </button>
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
              <a href="#" class="terms-link">{{ t('auth.privacyPolicy') }}</a>
              <span class="terms-text">{{ t('auth.agreementConjunction') }}</span>
              <a href="#" class="terms-link">{{ t('auth.termsOfService') }}</a>
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
@use '../styles/auth-shared' as *;

.login-container {
  @include auth-page;
}

.earth-background {
  @include auth-background;
}

.login-card {
  @include auth-card;
}

.welcome-title {
  @include auth-title;
}

.input-field {
  @include auth-input;
}

/* 这三页压在一张固定的深色背景图上，标签色不跟随明暗主题，保持浅灰即可 */
:deep(.ant-form-item-label > label) {
  @include auth-form-label;
}

.form-options {
  margin-bottom: 20px;

  @include auth-checkbox-look;

  :deep(.ant-checkbox-wrapper) {
    color: rgba(255, 255, 255, 0.8);
  }

  // 这里不能用 auth-link：它是「忘记密码」路由链接，原来就没设 text-decoration:none，
  // 一直保留浏览器默认下划线，和其余几个纯文字链接不是同一种效果
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
  @include auth-link;
}

.register-wrapper {
  text-align: center;
  font-size: 14px;
}

.register-text {
  @include auth-hint-text;
}

.register-link {
  @include auth-link;
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
  @include auth-send-code-base;

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