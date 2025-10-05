<template>
  <div class="forget-container">
    <!-- 地球背景 -->
    <div class="earth-background"></div>
    
    <!-- 忘记密码区域 -->
    <a-row type="flex" justify="center" align="middle" style="min-height: 100vh;">
      <a-col :xs="22" :sm="18" :md="12" :lg="10" :xl="8">
        <a-card class="forget-card" :bordered="false">
          <!-- 标题 -->
          <div class="welcome-title">
            找回密码
          </div>
          
          <!-- 忘记密码表单 -->
          <a-form-model
            ref="forgetForm"
            :model="forgetForm"
            @submit="handleSubmit"
            layout="vertical"
          >
            <!-- 邮箱输入 -->
            <a-form-model-item label="邮箱">
              <a-input
                v-model="forgetForm.email"
                placeholder="请输入注册邮箱"
                size="large"
                class="input-field"
              >
                <a-icon slot="prefix" type="mail" />
                <span 
                  slot="suffix" 
                  class="send-code-btn"
                  :class="{ 
                    'disabled': !forgetForm.email || sendCodeLoading || countdown > 0,
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
            
            <!-- 验证码输入 -->
            <a-form-model-item label="邮箱验证码" v-if="showVerificationInput">
              <a-input
                v-model="forgetForm.verificationCode"
                placeholder="请输入验证码"
                size="large"
                class="input-field"
              >
                <a-icon slot="prefix" type="safety-certificate" />
              </a-input>
            </a-form-model-item>
            
            <!-- 新密码输入 -->
            <a-form-model-item label="新密码" v-if="showPasswordInput">
              <a-input-password
                v-model="forgetForm.newPassword"
                placeholder="请输入新密码"
                size="large"
                class="input-field"
              >
                <a-icon slot="prefix" type="lock" />
              </a-input-password>
            </a-form-model-item>
            
            <!-- 确认新密码输入 -->
            <a-form-model-item label="确认新密码" v-if="showPasswordInput">
              <a-input-password
                v-model="forgetForm.confirmPassword"
                placeholder="请再次输入新密码"
                size="large"
                class="input-field"
              >
                <a-icon slot="prefix" type="lock" />
              </a-input-password>
            </a-form-model-item>
            
            <!-- 提交按钮 -->
            <a-form-model-item style="margin-top: 24px;">
              <a-button 
                type="primary" 
                html-type="submit" 
                :loading="loading"
                block
                size="large"
                class="forget-button"
              >
                {{ showPasswordInput ? '重置密码' : '验证邮箱' }}
              </a-button>
            </a-form-model-item>
            
            <!-- 返回登录链接 -->
            <div class="back-login-wrapper">
              <span class="back-text">想起密码了？</span>
              <router-link to="login" class="back-link">
                返回登录
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
      forgetForm: {
        email: '',
        verificationCode: '',
        newPassword: '',
        confirmPassword: ''
      },
      loading: false,
      sendCodeLoading: false,
      showVerificationInput: false,
      showPasswordInput: false,
      countdown: 0,
      countdownTimer: null,
      isEmailVerified: false
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
      
      if (!this.forgetForm.email) {
        this.showError('请先输入邮箱地址！')
        return
      }
      
      // 简单邮箱格式验证
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
      if (!emailRegex.test(this.forgetForm.email)) {
        this.showError('请输入正确的邮箱格式！')
        return
      }
      
      this.sendCodeLoading = true
      
      // 调用发送验证码的API
      axios.jsonPost({
        url: api.user.sendEmailCaptcha,
        data: {
          email: this.forgetForm.email,
          type: 'forget'
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
      
      if (!this.showPasswordInput) {
        // 第一步：验证邮箱验证码
        this.verifyEmailCode()
      } else {
        // 第二步：重置密码
        this.resetPassword()
      }
    },
    
    // 验证邮箱验证码
    verifyEmailCode() {
      // 手动验证
      if (!this.forgetForm.email) {
        this.showError('请输入邮箱！')
        return
      }
      
      if (!this.showVerificationInput) {
        this.showError('请先发送邮箱验证码！')
        return
      }
      
      if (!this.forgetForm.verificationCode) {
        this.showError('请输入邮箱验证码！')
        return
      }
      
      this.loading = true
      
      axios.get({
        url: api.user.checkCaptcha,
        data: {
          code: this.forgetForm.verificationCode,
          email: this.forgetForm.email,
          type: 'forget'
        }
      }).then(res => {
        this.loading = false
        if (res.code === 200) {
          this.$message.success('验证成功！请设置新密码')
          this.isEmailVerified = true
          this.showPasswordInput = true
        } else {
          this.showError(res.message)
        }
      }).catch(() => {
        this.loading = false
        this.showError('验证码验证失败！')
      })
    },
    
    // 重置密码
    resetPassword() {
      // 手动验证
      if (!this.forgetForm.newPassword) {
        this.showError('请输入新密码！')
        return
      }
      
      if (!this.forgetForm.confirmPassword) {
        this.showError('请确认新密码！')
        return
      }
      
      if (this.forgetForm.newPassword !== this.forgetForm.confirmPassword) {
        this.showError('两次输入的密码不一致！')
        return
      }
      
      if (this.forgetForm.newPassword.length < 6) {
        this.showError('密码长度不能少于6位！')
        return
      }
      
      this.loading = true
      
      axios.jsonPost({
        url: api.user.update,
        data: {
          email: this.forgetForm.email,
          code: this.forgetForm.verificationCode,
          password: this.forgetForm.newPassword
        }
      }).then(res => {
        this.loading = false
        if (res.code === 200) {
          setTimeout(() => {
            this.$router.push('/login')
          }, 500)
        } else {
          this.showError(res.message || '密码重置失败！')
        }
      }).catch(() => {
        this.loading = false
        this.showError('密码重置失败！')
      })
    }
  }
}
</script>

<style lang="scss" scoped>
// 主容器
.forget-container {
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

// 忘记密码卡片
.forget-card {
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