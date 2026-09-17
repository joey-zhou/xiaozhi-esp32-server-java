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

const agreeTermsRules = [{
  validator: (_rule: unknown, value: boolean) =>
    value ? Promise.resolve() : Promise.reject(t('auth.agreeTermsRequired')),
  trigger: 'change',
}]

const handleSendCode = async () => {
  try {
    await formRef.value?.validateFields(['email', 'username'])
    const success = await sendRegisterCode(formData.email, formData.username)
    if (success) {
      showVerificationInput.value = true
    }
  } catch {
    // 表单校验未通过时不发送验证码
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
            <a-form-item
              name="agreeTerms"
              :rules="agreeTermsRules"
            >
              <a-checkbox v-model:checked="formData.agreeTerms">
                {{ t('auth.agreeTerms') }}
                <a href="#" class="terms-link">{{ t('auth.userAgreement') }}</a>
                {{ t('auth.agreementConjunction') }}
                <a href="#" class="terms-link">{{ t('auth.privacyPolicy') }}</a>
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
@use '../styles/auth-shared' as *;

// 主容器
.register-container {
  @include auth-page;
}

// 背景图片
.earth-background {
  @include auth-background;
}

// 注册卡片
.register-card {
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

// 用户协议
:deep(.ant-checkbox-wrapper) {
  color: rgba(255, 255, 255, 0.8);
  font-size: 12px;
  line-height: 1.4;
}

@include auth-checkbox-look;

.terms-link {
  @include auth-link;
  font-size: 12px;
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
  @include auth-hint-text;
}

.login-link {
  @include auth-link;
}
</style>