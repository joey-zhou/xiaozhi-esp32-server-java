<template>
  <div class="login-container">
    <!-- 地球背景 -->
    <div class="earth-background"></div>
    
    <!-- 登录区域 -->
    <a-row type="flex" justify="center" align="middle" style="min-height: 100vh;">
      <a-col :xs="22" :sm="18" :md="12" :lg="10" :xl="8">
        <a-card class="login-card" :bordered="false">
          <!-- 标题 -->
          <div class="welcome-title">
            欢迎回来
          </div>
          
          <!-- 登录表单 -->
          <a-form-model
            ref="loginForm"
            :model="loginForm"
            @submit="handleSubmit"
            layout="vertical"
          >
            <!-- 账号输入 -->
            <a-form-model-item label="账号">
              <a-input
                v-model="loginForm.username"
                placeholder="请输入账号"
                size="large"
                class="input-field"
              >
                <a-icon slot="prefix" type="user" />
              </a-input>
            </a-form-model-item>
            
            <!-- 密码输入 -->
            <a-form-model-item label="密码">
              <a-input-password
                v-model="loginForm.password"
                placeholder="请输入密码"
                size="large"
                class="input-field"
              >
                <a-icon slot="prefix" type="lock" />
              </a-input-password>
            </a-form-model-item>
            
            <!-- 记住我和忘记密码 -->
            <a-row type="flex" justify="space-between" align="middle" class="form-options">
              <a-col>
                <a-checkbox v-model="loginForm.rememberMe">
                  记住我
                </a-checkbox>
              </a-col>
              <a-col>
                <router-link to="forget">
                  忘记密码？
                </router-link>
              </a-col>
            </a-row>
            
            <!-- 登录按钮 -->
            <a-form-model-item style="margin-top: 24px;">
              <a-button 
                type="primary" 
                html-type="submit" 
                :loading="loading"
                block
                size="large"
                class="login-button"
              >
                登录
              </a-button>
            </a-form-model-item>
            
            <!-- 隐私协议与服务条款 -->
            <div class="privacy-terms">
              <span class="terms-text">登录即表示您同意</span>
              <a href="#" class="terms-link">《隐私协议》</a>
              <span class="terms-text">和</span>
              <a href="#" class="terms-link">《服务条款》</a>
            </div>
            
            <!-- 分割线 -->
            <a-divider>
              <span class="divider-text">其他方式登录</span>
            </a-divider>
            
            <!-- 第三方登录 -->
            <a-row type="flex" justify="center" :gutter="20" style="margin-bottom: 24px;">
              <a-col>
                <a-button type="default" shape="circle" size="large" class="social-button wechat">
                  <a-icon type="wechat" />
                </a-button>
              </a-col>
              <a-col>
                <a-button type="default" shape="circle" size="large" class="social-button qq">
                  <a-icon type="qq" />
                </a-button>
              </a-col>
              <a-col>
                <a-button type="default" shape="circle" size="large" class="social-button mobile">
                  <a-icon type="mobile" />
                </a-button>
              </a-col>
            </a-row>
            
            <!-- 注册链接 -->
            <div class="register-wrapper">
              <span class="register-text">没有账号？</span>
              <router-link to="register" class="register-link">
                免费注册
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
import Cookies from 'js-cookie'
import mixin from "@/mixins/index"
import { encrypt, decrypt } from '@/utils/jsencrypt'

export default {
  mixins: [mixin],
  data() {
    return {
      loginForm: {
        username: '',
        password: '',
        rememberMe: false
      },
      loading: false
    }
  },
  mounted() {
    this.getCookie()
  },
  methods: {
    getCookie() {
      const username = Cookies.get('username')
      const password = Cookies.get('rememberMe')
      this.loginForm = {
        username: username === undefined ? this.loginForm.username : username,
        password: password === undefined ? this.loginForm.password : decrypt(password),
        rememberMe: password === undefined ? false : Boolean(password)
      }
    },
    handleSubmit(e) {
      e.preventDefault()
      
      // 手动验证
      if (!this.loginForm.username) {
        this.$message.error('请输入账号！')
        return
      }
      
      if (!this.loginForm.password) {
        this.$message.error('请输入密码！')
        return
      }
      
      this.loading = true
      axios
        .jsonPost({
          url: api.user.login,
          data: {
            ...this.loginForm
          }
        }).then(res => {
          this.loading = false
          if (res.code === 200) {
            Cookies.set('userInfo', JSON.stringify(res.data), { expires: 30 })
            if (this.loginForm.rememberMe) {
              Cookies.set('username', this.loginForm.username, { expires: 30 })
              Cookies.set('rememberMe', encrypt(this.loginForm.password), { expires: 30 })
            } else {
              Cookies.remove('username')
              Cookies.remove('rememberMe')
            }
            this.$store.commit('USER_INFO', res.data)
            this.$router.push('/dashboard')
          } else {
            this.$message.error(res.message)
          }
        }).catch(() => {
          this.loading = false
          this.showError();
        })
    }
  }
}
</script>

<style lang="scss" scoped>
// 全局变量

// 主容器
.login-container {
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


// 登录卡片
.login-card {
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
    background: #888888 !important;
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

// 表单选项
.form-options {
  margin-bottom: 20px;
  
  >>> .ant-checkbox-wrapper {
    color: rgba(255, 255, 255, 0.8);
  }
  
  >>> .ant-checkbox-inner {
    background-color: rgba(255, 255, 255, 0.1) !important;
    border-color: rgba(255, 255, 255, 0.3) !important;
  }
  
  >>> .ant-checkbox-checked .ant-checkbox-inner {
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

// 登录按钮
.login-button {
  height: 36px !important;
  transition: all 0.3s ease !important;
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 20px rgba(66, 133, 244, 0.3) !important;
  }
}

// 隐私协议与服务条款
.privacy-terms {
  text-align: center;
  margin: 16px 0;
  font-size: 12px;
  line-height: 1.5;
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

// 注册链接
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

// 第三方登录按钮
.social-button {
  background: rgba(255, 255, 255, 0.1) !important;
  border: none !important;
  color: #ffffff !important;
  transition: all 0.3s ease;
  
  &:hover {
    transform: translateY(-2px);
  }
  
  &.wechat:hover {
    background: rgba(9, 187, 7, 0.2) !important;
    color: #09bb07 !important;
    border-color: #09bb07 !important;
  }
  
  &.qq:hover {
    background: rgba(18, 183, 245, 0.2) !important;
    color: #12b7f5 !important;
    border-color: #12b7f5 !important;
  }
  
  &.mobile:hover {
    background: rgba(255, 140, 0, 0.2) !important;
    color: #ff8c00 !important;
    border-color: #ff8c00 !important;
  }
}

// 全局文字颜色调整
>>> .ant-typography {
  color: #ffffff !important;
}

>>> .ant-divider-inner-text {
  color: rgba(255, 255, 255, 0.6) !important;
}

// 分割线文本样式
.divider-text {
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
}

>>> .ant-divider-horizontal.ant-divider-with-text::before,
>>> .ant-divider-horizontal.ant-divider-with-text::after {
  border-top-color: rgba(255, 255, 255, 0.2) !important;
}

// 链接样式
a {
  color: #4285f4;
  
  &:hover {
    color: #3367d6;
  }
}
</style>

