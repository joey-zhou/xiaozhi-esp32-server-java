<script setup lang="ts">
import { GithubOutlined, QqOutlined, CopyrightOutlined, WechatOutlined } from '@ant-design/icons-vue'

const currentYear = new Date().getFullYear()

// 微信号或微信二维码链接
const wechatQrCodeValue = 'https://weixin.qq.com/your-wechat-link' // 可以替换为你的微信号或链接
</script>

<template>
  <div class="app-footer">
    <div class="footer-links">
      <a
        title="GitHub"
        target="_blank"
        href="https://github.com/joey-zhou"
        class="footer-link"
      >
        <GithubOutlined />
      </a>
      
      <a
        title="Gitee"
        target="_blank"
        href="https://gitee.com/joey-zhou"
        class="footer-link"
      >
        <svg
          class="icon-svg"
          viewBox="0 0 1024 1024"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            d="M512 1024C230.4 1024 0 793.6 0 512S230.4 0 512 0s512 230.4 512 512-230.4 512-512 512z m259.2-569.6H480c-12.8 0-25.6 12.8-25.6 25.6v64c0 12.8 12.8 25.6 25.6 25.6h176c12.8 0 25.6 12.8 25.6 25.6v12.8c0 41.6-35.2 76.8-76.8 76.8h-240c-12.8 0-25.6-12.8-25.6-25.6V416c0-41.6 35.2-76.8 76.8-76.8h355.2c12.8 0 25.6-12.8 25.6-25.6v-64c0-12.8-12.8-25.6-25.6-25.6H416c-105.6 0-188.8 86.4-188.8 188.8V768c0 12.8 12.8 25.6 25.6 25.6h374.4c92.8 0 169.6-76.8 169.6-169.6v-144c0-12.8-12.8-25.6-25.6-25.6z"
            fill="currentColor"
          />
        </svg>
      </a>
      
      <a
        title="QQ"
        target="_blank"
        href="http://wpa.qq.com/msgrd?v=3&uin=1277676045&site=qq&menu=yes"
        class="footer-link"
      >
        <QqOutlined />
      </a>
      
      <a-popover placement="top" class="footer-link">
        <template #content>
          <div class="wechat-qrcode-container">
            <a-qrcode :value="wechatQrCodeValue" :size="160" />
          </div>
        </template>
        <WechatOutlined />
      </a-popover>
    </div>
    
    <div class="footer-copyright">
      Copyright
      <CopyrightOutlined style="margin: 0 4px" />
      {{ currentYear }} By JoeyZhou. All rights reserved
    </div>
  </div>
</template>

<style scoped lang="scss">
.app-footer {
  text-align: center;
  padding: 2px 0;
}

.footer-links {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 40px;
  
  .footer-link {
    color: var(--ant-color-text-secondary);
    font-size: 16px;
    transition: all 0.3s;
    cursor: pointer;
    
    &:hover {
      color: var(--ant-color-text);
    }
    
    .icon-svg {
      width: 1em;
      height: 1em;
      vertical-align: -0.125em;
      fill: currentColor;
    }
  }
}

.footer-copyright {
  color: var(--ant-color-text-secondary);
  font-size: 14px;
  transition: all 0.3s;
}

.wechat-qrcode-container {
  display: flex;
  flex-direction: column;
  align-items: center;
}
</style>

