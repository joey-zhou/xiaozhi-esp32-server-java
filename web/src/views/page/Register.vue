<template>
  <div class="register-container">
    <!-- 地球背景 -->
    <div class="earth-background"></div>
    
    <!-- 注册区域 -->
    <a-row type="flex" justify="center" align="middle" style="min-height: 100vh;">
      <a-col :xs="22" :sm="18" :md="12" :lg="10" :xl="8">
        <a-card class="register-card" :bordered="false">
          <!-- 标题 -->
          <div class="welcome-title">
            注册账号
          </div>
          
          <!-- 注册表单 -->
          <a-form-model
            ref="registerForm"
            :model="formData"
            @submit="handleSubmit"
            layout="vertical"
          >
            <!-- 姓名输入 -->
            <a-form-model-item label="姓名">
              <a-input
                v-model="formData.name"
                placeholder="请输入姓名"
                size="large"
                class="input-field"
              >
                <a-icon slot="prefix" type="user" />
              </a-input>
            </a-form-model-item>
            
            <!-- 账号输入 -->
            <a-form-model-item label="账号">
              <a-input
                v-model="formData.username"
                placeholder="请输入账号"
                size="large"
                class="input-field"
              >
                <a-icon slot="prefix" type="idcard" />
              </a-input>
            </a-form-model-item>
            
            <!-- 邮箱输入 -->
            <a-form-model-item label="邮箱">
              <a-input
                v-model="formData.email"
                placeholder="请输入邮箱"
                size="large"
                class="input-field"
              >
                <a-icon slot="prefix" type="mail" />
                <span 
                  slot="suffix" 
                  class="send-code-btn"
                  :class="{ 
                    'disabled': !formData.email || sendCodeLoading || countdown > 0,
                    'loading': sendCodeLoading
                  }"
                  @click="sendVerificationCode"
                >
                  <a-icon v-if="sendCodeLoading" type="loading" />
                  {{ sendCodeLoading ? '发送中...' : 
                     countdown > 0 ? `${countdown}s后重发` : '发送验证码' }}
                </span>
              </a-input>
            </a-form-model-item>
            
            <!-- 电话输入 -->
            <a-form-model-item label="电话">
              <a-input
                v-model="formData.tel"
                placeholder="请输入电话号码"
                size="large"
                class="input-field"
              >
                <a-icon slot="prefix" type="phone" />
              </a-input>
            </a-form-model-item>
            
            <!-- 验证码输入 -->
            <a-form-model-item label="邮箱验证码" v-if="showVerificationInput">
              <a-input
                v-model="formData.verifyCode"
                placeholder="请输入验证码"
                size="large"
                class="input-field"
              >
                <a-icon slot="prefix" type="safety-certificate" />
              </a-input>
            </a-form-model-item>
            
            <!-- 密码输入 -->
            <a-form-model-item label="密码">
              <a-input-password
                v-model="formData.password"
                placeholder="请输入密码"
                size="large"
                class="input-field"
              >
                <a-icon slot="prefix" type="lock" />
              </a-input-password>
            </a-form-model-item>
            
            <!-- 确认密码输入 -->
            <a-form-model-item label="确认密码">
              <a-input-password
                v-model="formData.confirmPassword"
                placeholder="请再次输入密码"
                size="large"
                class="input-field"
              >
                <a-icon slot="prefix" type="lock" />
              </a-input-password>
            </a-form-model-item>
            
            <!-- 用户协议 -->
            <div class="agreement-wrapper">
              <a-checkbox v-model="formData.agreeTerms">
                我已阅读并同意
                <a href="#" class="terms-link">《用户协议》</a>
                和
                <a href="#" class="terms-link">《隐私政策》</a>
              </a-checkbox>
            </div>
            
            <!-- 注册按钮 -->
            <a-form-model-item style="margin-top: 24px;">
              <a-button 
                type="primary" 
                html-type="submit" 
                :loading="loading"
                block
                size="large"
                class="register-button"
              >
                注册
              </a-button>
            </a-form-model-item>
            
            <!-- 登录链接 -->
            <div class="login-wrapper">
              <span class="login-text">已有账号？</span>
              <router-link to="login" class="login-link">
                立即登录
              </router-link>
            </div>
          </a-form-model>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script>
import axios from '@/services/axios'
import api from '@/services/api'
import mixin from "@/mixins/index"

export default {
  mixins: [mixin],
  data() {
    return {
      formData: {
        name: '',
        username: '',
        email: '',
        tel: '',
        password: '',
        confirmPassword: '',
        verifyCode: '',
        agreeTerms: false
      },
      loading: false,
      sendCodeLoading: false,
      showVerificationInput: false,
      countdown: 0,
      countdownTimer: null,
      currentStep: 1
    }
  },
  beforeDestroy() {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer)
    }
  },
  methods: {
    // 发送验证码
    sendVerificationCode() {
      // 检查是否已经在发送中或倒计时中
      if (this.sendCodeLoading || this.countdown > 0) {
        return
      }
      
      if (!this.formData.email) {
        this.showError('请先输入邮箱地址！')
        return
      }
      
      // 简单邮箱格式验证
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
      if (!emailRegex.test(this.formData.email)) {
        this.showError('请输入正确的邮箱格式！')
        return
      }
      
      this.sendCodeLoading = true
      
      // 先检查用户名和邮箱是否已存在
      axios.get({
        url: api.user.checkUser,
        data: {
          username: this.formData.username,
          email: this.formData.email
        }
      }).then(res => {
        if (res.code === 200) {
          // 用户名和邮箱都可用，发送验证码
          return this.sendVerifyCode()
        } else {
          this.sendCodeLoading = false
          this.showError(res.message)
        }
      }).catch(() => {
        this.sendCodeLoading = false
        this.showError('服务器错误，请稍后重试')
      })
    },
    
    // 发送验证码
    sendVerifyCode() {
      return axios.jsonPost({
        url: api.user.sendEmailCaptcha,
        data: {
          email: this.formData.email,
          type: 'register'
        }
      }).then(res => {
        this.sendCodeLoading = false
        if (res.code === 200) {
          this.$message.success('验证码已发送到您的邮箱！')
          this.showVerificationInput = true
          this.startCountdown()
        } else {
          this.showError(res.message || '发送验证码失败！')
        }
      }).catch(() => {
        this.sendCodeLoading = false
        this.showError('发送验证码失败！')
      })
    },
    
    // 开始倒计时
    startCountdown() {
      this.countdown = 60
      this.countdownTimer = setInterval(() => {
        this.countdown--
        if (this.countdown <= 0) {
          clearInterval(this.countdownTimer)
          this.countdownTimer = null
        }
      }, 1000)
    },
    
    // 表单提交
    handleSubmit(e) {
      e.preventDefault()
      
      // 手动验证
      if (!this.formData.name) {
        this.showError('请输入姓名！')
        return
      }
      
      if (!this.formData.username) {
        this.showError('请输入账号！')
        return
      }
      
      if (!this.formData.email) {
        this.showError('请输入邮箱！')
        return
      }
      
      if (!this.showVerificationInput) {
        this.showError('请先发送邮箱验证码！')
        return
      }
      
      if (!this.formData.verifyCode) {
        this.showError('请输入邮箱验证码！')
        return
      }
      
      if (!this.formData.password) {
        this.showError('请输入密码！')
        return
      }
      
      if (!this.formData.confirmPassword) {
        this.showError('请确认密码！')
        return
      }
      
      if (this.formData.password !== this.formData.confirmPassword) {
        this.showError('两次输入的密码不一致！')
        return
      }
      
      if (!this.formData.agreeTerms) {
        this.showError('请先同意用户协议和隐私政策！')
        return
      }
      
      // 执行注册
      this.doRegister()
    },
    
    // 执行注册
    doRegister() {
      this.loading = true
      
      // 调用后端API验证验证码并注册
      axios.jsonPost({
        url: api.user.add,
        data: {
          name: this.formData.name,
          username: this.formData.username,
          email: this.formData.email,
          tel: this.formData.tel,
          password: this.formData.password,
          code: this.formData.verifyCode
        }
      }).then(res => {
        this.loading = false
        if (res.code === 200) {
          setTimeout(() => {
            this.$router.push('/login')
          }, 500)
        } else {
          this.showError(res.message || '验证码错误或已过期')
        }
      }).catch(() => {
        this.loading = false
        this.showError('服务器错误，请稍后重试')
      })
    }
  }
}
</script>

<style lang="scss" scoped>
// 主容器
.register-container {
  position: relative;
  min-height: 100vh;
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
  
  >>> .ant-card-body {
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
  background: rgba(255,255,255,.1) !important;
  border: none !important;
  box-shadow: none !important;
  
  &:hover {
    background: rgba(255,255,255,.13) !important;
    border: none !important;
    box-shadow: none !important;
  }
  
  &:focus,
  &.ant-input-focused {
    background: rgba(255,255,255,.15) !important;
    border: none !important;
    box-shadow: 0 0 0 2px rgba(66, 133, 244, 0.2) !important;
  }
  
  >>> input {
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
  
  >>> .ant-input-prefix {
    color: rgba(255, 255, 255, 0.6) !important;
  }
  
  >>> .ant-input-password-icon {
    color: rgba(255, 255, 255, 0.6) !important;
  }
  
  >>> .ant-input-affix-wrapper {
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
>>> .ant-form-item-label > label {
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
.agreement-wrapper {
  margin-bottom: 20px;
  text-align: center;
  
  >>> .ant-checkbox-wrapper {
    color: rgba(255, 255, 255, 0.8);
    font-size: 10px;
    line-height: 1.4;
  }
  
  >>> .ant-checkbox-inner {
    background-color: rgba(255, 255, 255, 0.1) !important;
    border-color: rgba(255, 255, 255, 0.3) !important;
  }
  
  >>> .ant-checkbox-checked .ant-checkbox-inner {
    background-color: #4285f4 !important;
    border-color: #4285f4 !important;
  }
}

.terms-link {
  color: #4285f4;
  text-decoration: none;
  font-size: 10px;
  
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