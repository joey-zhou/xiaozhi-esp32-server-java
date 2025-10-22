<template>
  <transition name="scale" @after-leave="$emit('close')">
    <div v-if="internalVisible" class="global-chat-popup">
      <div class="chat-popup-container">
        <!-- 标题栏 -->
        <div class="chat-popup-header">
          <div class="header-left">
            <div class="connection-status">
              <div 
                class="status-dot"
                :class="{ 'connected': wsIsConnected, 'disconnected': !wsIsConnected }"
              />
              <span class="status-text">{{ wsConnectionStatus }}</span>
            </div>
          </div>
          
          <div class="header-title">
            <a-icon type="message" />
            <span>小智助手</span>
          </div>
          
          <div class="header-actions">
            <a-button type="text" size="small" @click="handleClose" title="关闭">
              <a-icon type="close" />
            </a-button>
          </div>
        </div>
        
        <!-- 聊天内容 -->
        <div class="chat-popup-content">
          <ChatComponent
            ref="chatComponentRef"
            :message-list="messages"
            :show-input="true"
            :show-voice-toggle="false"
            :user-avatar="userAvatar"
            :ai-avatar="aiAvatar"
            :input-placeholder="'输入消息...'"
            :empty-text="'暂无对话，开始聊天吧'"
            :is-connected-prop="wsIsConnected"
            :avatar-size="32"
          />
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
import ChatComponent from '@/components/ChatComponent'
import { 
  messages,
  clearMessages
} from '@/services/websocketService'
import websocketMixin from '@/mixins/websocketMixin'
import { getResourceUrl } from '@/services/axios'

export default {
  name: 'GlobalChatPopup',
  mixins: [websocketMixin],
  components: {
    ChatComponent
  },
  props: {
    visible: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      messages: messages,
      internalVisible: false
    }
  },
  computed: {
    // 用户头像
    userAvatar() {
      const userInfo = this.$store.getters.USER_INFO;
      if (userInfo && userInfo.avatar) {
        return getResourceUrl(userInfo.avatar);
      }
      return '/assets/user-avatar.png';
    },

    // AI头像（保留兼容性，但不再使用）
    aiAvatar() {
      return '/assets/ai-avatar.png';
    }
  },
  watch: {
    visible(newVal) {
      // 当外部visible属性变化时，更新内部状态
      this.internalVisible = newVal;
    }
  },
  mounted() {
    // 初始化内部状态
    this.internalVisible = this.visible;
  },
  methods: {
    // 处理关闭
    handleClose() {
      // 先设置内部状态为false，触发动画
      this.internalVisible = false;
      // 同时通知父组件
      this.$emit('close');
    },
    
    // 处理切换连接状态 - 使用mixin中的方法
    async handleToggleConnection() {
      if (this.wsIsConnected) {
        await this.disconnectWebSocket()
      } else {
        await this.connectWebSocket()
      }
    },
    
    // 处理清空消息
    handleClearMessages() {
      clearMessages()
      this.$message.success('已清空对话记录')
    },
    
    // 跳转到全屏聊天
    handleGoToFullChat() {
      this.$emit('go-to-chat')
    },
    
    // 处理录音开始
    handleRecordingStart() {
      console.log('录音开始')
    },
    
    // 处理录音结束
    handleRecordingStop() {
      console.log('录音结束')
    },
    
    // 处理录音错误
    handleRecordingError(error) {
      console.error('录音错误:', error)
    },
    
    // 处理模式变化
    handleModeChange(isVoiceMode) {
      console.log('模式变化:', isVoiceMode ? '语音' : '文字')
    },
    
    // 处理WebSocket消息（由mixin调用）
    handleWebSocketMessage(data) {
      console.log('全局聊天框收到消息:', data)
    },
    
    // 处理二进制音频消息
    handleBinaryAudioMessage(audioData) {
      if (this.$refs.chatComponentRef) {
        return this.$refs.chatComponentRef.handleBinaryAudioMessage(audioData)
      }
    }
  }
}
</script>

<style scoped>
.global-chat-popup {
  position: fixed;
  bottom: 90px;
  right: 24px;
  z-index: 1001;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  transform-origin: bottom right; /* 设置变换的原点在右下角 */
}

.chat-popup-container {
  width: 380px;
  height: 500px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  border: 1px solid #d6d6d6;
  display: flex;
  flex-direction: column;
}

/* 缩放动画 - 进入 */
.scale-enter-active {
  animation: scale-in 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275); /* 弹性缓动 */
}

/* 缩放动画 - 离开 */
.scale-leave-active {
  animation: scale-out 0.25s cubic-bezier(0.55, 0.055, 0.675, 0.19); /* 快速缓出 */
}

@keyframes scale-in {
  0% {
    transform: scale(0.3);
    opacity: 0;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

@keyframes scale-out {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  100% {
    transform: scale(0.3);
    opacity: 0;
  }
}

.chat-popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f7f7f7;
  color: #333;
  border-radius: 8px 8px 0 0;
  border-bottom: 1px solid #d6d6d6;
  user-select: none;
}

.header-left {
  display: flex;
  align-items: center;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4d4f;
}

.status-dot.connected {
  background: #52c41a;
}

.status-text {
  font-size: 12px;
  opacity: 0.9;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  font-size: 16px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-actions .ant-btn {
  color: #666;
  border: none;
  background: transparent;
  width: 24px;
  height: 24px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-actions .ant-btn:hover {
  background: rgba(0, 0, 0, 0.05);
  color: #333;
}

.chat-popup-content {
  flex: 1;
  overflow: hidden;
  background: #f5f5f5;
  border-radius: 0 0 8px 8px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .global-chat-popup {
    bottom: 80px;
    right: 16px;
    left: 16px;
    transform-origin: bottom center; /* 在移动设备上改变变换原点 */
  }
  
  .chat-popup-container {
    width: 100%;
    height: 400px;
  }
  
  .header-title {
    font-size: 14px;
  }
  
  .connection-status {
    display: none;
  }
}

/* 暗色主题支持 */
@media (prefers-color-scheme: dark) {
  .chat-popup-container {
    background: #1f1f1f;
    border-color: #333;
  }
  
  .chat-popup-content {
    background: #2a2a2a;
  }
}
</style>