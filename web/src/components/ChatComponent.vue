<template>
  <div class="chat-component" :style="{ height: height }">
    <!-- 聊天内容区域 -->
    <div class="chat-content" ref="chatContentRef">
      <div v-if="messages.length === 0" class="empty-chat">
        <a-empty :description="emptyText" />
      </div>
      <div v-else class="chat-messages">
        <div v-for="(message, index) in messages" :key="index">
          <!-- 时间戳 -->
          <div v-if="showTimestamp(message, index)" class="message-timestamp">
            {{ formatTimestamp(message.timestamp) }}
          </div>
          
          <!-- 消息内容 -->
          <div class="message-wrapper" :class="message.isUser ? 'user-message' : 'ai-message'">
            <!-- 头像 -->
            <div class="avatar">
              <a-avatar v-if="message.isUser" :src="userAvatar" :size="avatarSize" />
              <AiAvatar v-else :size="avatarSize" />
            </div>
            
            <!-- 消息气泡 -->
            <div class="message-content">
              <div class="message-bubble" :class="{ 'clickable': messageClickable }" @click="onMessageClick(message)">
                <div v-if="message.type === 'text'" class="message-text">
                  {{ message.content }}
                </div>
                <div v-else-if="message.type === 'audio'" class="audio-message">
                  <a-icon type="sound" />
                  <span class="audio-duration">{{ message.duration || '0:00' }}</span>
                  <div class="audio-wave">
                    <div v-for="i in 4" :key="i" class="wave-bar" :class="{ active: message.isPlaying }"></div>
                  </div>
                </div>
                <div v-else-if="message.type === 'stt'" class="message-text stt-message">
                  <a-icon type="audio" /> {{ message.content }}
                </div>
                <div v-else-if="message.type === 'tts'" class="message-text tts-message">
                  <a-icon type="sound" /> {{ message.content }}
                </div>
                <div v-else-if="message.type === 'system'" class="message-text system-message">
                  {{ message.content }}
                </div>
              </div>
              
              <!-- 加载指示器 -->
              <div v-if="message.isLoading" class="loading-indicator">
                <a-spin size="small" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 可选的输入区域 -->
    <div v-if="showInput" class="chat-input-area">
      <!-- 文本输入 -->
      <div class="text-input-wrapper">
        <a-textarea
          v-model="inputMessage"
          :placeholder="inputPlaceholder"
          :auto-size="{ minRows: 1, maxRows: 4 }"
          @keypress.enter="handleEnterKey"
        />

        <!-- 发送按钮 -->
        <a-button
          type="primary"
          class="send-button"
          :disabled="!inputMessage.trim()"
          @click="sendTextMessage"
        >
          发送
        </a-button>
      </div>
    </div>
  </div>
</template>

<script>
import {
  // WebSocket相关
  sendTextMessage as wsSendTextMessage,
  startDirectRecording,
  stopDirectRecording,
  isWebSocketConnected,
} from '@/services/websocketService';

// 引入音频处理服务
import {
  handleBinaryMessage,
  getAudioState
} from '@/services/audioService';

// 引入WebSocket mixin
import websocketMixin from '@/mixins/websocketMixin';

// 引入AI头像组件
import AiAvatar from '@/components/AiAvatar';

export default {
  name: 'ChatComponent',
  mixins: [websocketMixin],
  components: {
    AiAvatar
  },
  props: {
    // 是否显示输入框
    showInput: {
      type: Boolean,
      default: false
    },
    // 是否显示语音切换按钮
    showVoiceToggle: {
      type: Boolean,
      default: true
    },
    // 传入的消息列表
    messageList: {
      type: Array,
      default: () => []
    },
    // 用户头像
    userAvatar: {
      type: String,
      default: '/assets/user-avatar.png'
    },
    // AI头像
    aiAvatar: {
      type: String,
      default: '/assets/ai-avatar.png'
    },
    // 组件高度
    height: {
      type: String,
      default: '100%'
    },
    // 空消息提示文本
    emptyText: {
      type: String,
      default: '暂无对话记录'
    },
    // 输入框占位符
    inputPlaceholder: {
      type: String,
      default: '输入消息...'
    },
    // 头像大小
    avatarSize: {
      type: Number,
      default: 40
    },
    // 消息是否可点击
    messageClickable: {
      type: Boolean,
      default: false
    },
    isConnectedProp: {
      type: Boolean,
      default: false
    },
    // 内容区域最大高度
    contentMaxHeight: {
      type: String,
      default: 'none'
    }
  },
  data() {
    return {
      inputMessage: '',
      messages: [],
      isVoiceMode: false,
      isRecording: false
    };
  },
  watch: {
    // 监听外部消息列表变化
    messageList: {
      handler(newVal) {
        this.messages = newVal;
        this.scrollToBottom();
      },
      immediate: true,
      deep: true
    }
  },
  mounted() {
    this.scrollToBottom();
  },
  methods: {
    // 处理回车键按下事件
    handleEnterKey(e) {
      // 阻止默认行为（换行）
      e.preventDefault();

      // 检查是否按下了修饰键
      if (e.shiftKey || e.ctrlKey || e.altKey || e.metaKey) {
        // 如果按下了修饰键，不发送消息
        return;
      }

      // 发送消息
      this.sendTextMessage();
    },

    // 滚动到底部
    scrollToBottom() {
      this.$nextTick(() => {
        if (this.$refs.chatContentRef) {
          this.$refs.chatContentRef.scrollTop = this.$refs.chatContentRef.scrollHeight;
        }
      });
    },

    // 统一的连接检查和自动连接逻辑
    async ensureWebSocketConnection() {
      // 检查连接状态，如果未连接则自动连接
      if (!this.wsIsConnected && !isWebSocketConnected()) {
        try {
          // 尝试安静自动连接
          const connected = await this.quietConnectWebSocket();

          if (!connected) {
            this.$message.error('未连接到服务器，请检查网络和配置');
            return false;
          }

          // 等待一点时间确保连接建立
          await new Promise(resolve => setTimeout(resolve, 300));
        } catch (error) {
          this.$message.error('连接失败: ' + error.message);
          return false;
        }
      }
      return true;
    },

    // 发送文本消息
    async sendTextMessage() {
      const text = this.inputMessage.trim();
      if (!text) return;

      // 确保WebSocket连接
      const connected = await this.ensureWebSocketConnection();
      if (!connected) return;

      // 发送到服务器
      const success = wsSendTextMessage(text);

      if (success) {
        // 清空输入框
        this.inputMessage = '';
        // 滚动到底部
        this.scrollToBottom();
      } else {
        this.$message.error('发送失败，请检查连接状态');
      }
    },

    // 消息点击事件
    onMessageClick(message) {
      if (this.messageClickable) {
        this.$emit('message-click', message);
      }
    },

    // 开始录音
    async startRecording() {
      if (this.isRecording) return;

      // 确保WebSocket连接
      const connected = await this.ensureWebSocketConnection();
      if (!connected) return;

      try {
        this.isRecording = true;
        await startDirectRecording();
        this.$emit('recording-start');
      } catch (error) {
        this.isRecording = false;
        this.$message.error('无法启动录音，请检查麦克风权限');
        this.$emit('recording-error', error);
      }
    },

    // 停止录音
    async stopRecording() {
      if (!this.isRecording) return;

      try {
        this.isRecording = false;
        await stopDirectRecording();
        this.$emit('recording-stop');
      } catch (error) {
        this.$message.error('停止录音失败');
        this.$emit('recording-error', error);
      }
    },

    // 切换输入模式
    toggleInputMode() {
      this.isVoiceMode = !this.isVoiceMode;
      this.$emit('mode-change', this.isVoiceMode);
    },

    // 显示时间戳
    showTimestamp(message, index) {
      if (index === 0) return true;

      const prevMsg = this.messages[index - 1];
      if (!prevMsg || !prevMsg.timestamp || !message.timestamp) return true;

      // 如果与上一条消息时间间隔超过5分钟，显示时间戳
      const prevTime = prevMsg.timestamp instanceof Date ? prevMsg.timestamp.getTime() : new Date(prevMsg.timestamp).getTime();
      const currTime = message.timestamp instanceof Date ? message.timestamp.getTime() : new Date(message.timestamp).getTime();

      return currTime - prevTime > 5 * 60 * 1000;
    },

    // 格式化时间戳
    formatTimestamp(timestamp) {
      if (!timestamp) return '';

      const date = timestamp instanceof Date ? timestamp : new Date(timestamp);
      return date.toLocaleString();
    },

    // 处理二进制音频消息 - 这是一个代理方法，调用audioService中的处理函数
    async handleBinaryAudioMessage(data) {
      return await handleBinaryMessage(data);
    },

    // 处理WebSocket消息（由mixin调用）
    handleWebSocketMessage(data) {
      if (!data || !data.type) return;
      // ChatComponent通常不需要特殊的消息处理逻辑
      // 消息处理主要在websocketService中完成
      console.log('ChatComponent收到WebSocket消息:', data);
    }
  }
};
</script>

<style scoped>
.chat-component {
  display: flex;
  flex-direction: column;
  background-color: #f5f5f5;
  border-radius: 0;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif;
}

.chat-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 12px 8px 12px;
  scroll-behavior: smooth;
  background: #f5f5f5;
}

.empty-chat {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  min-height: 300px;
  color: #999;
}

.empty-chat-icon {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 16px;
}

.chat-bubble-icon {
  animation: float 3s ease-in-out infinite;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0px);
  }
  50% {
    transform: translateY(-10px);
  }
}

.empty-description {
  text-align: center;
}

.empty-title {
  font-size: 16px;
  font-weight: 500;
  color: #666;
  margin-bottom: 8px;
  line-height: 1.5;
}

.empty-subtitle {
  font-size: 14px;
  color: #999;
  margin-bottom: 0;
  line-height: 1.4;
}

.empty-chat >>> .ant-empty {
  color: inherit;
}

.empty-chat >>> .ant-empty-image {
  margin-bottom: 16px;
}

.empty-chat >>> .ant-empty-description {
  color: inherit;
}

.chat-messages {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-bottom: 0;
}

.message-timestamp {
  text-align: center;
  margin: 16px 0 8px 0;
  color: #b2b2b2;
  font-size: 12px;
  line-height: 1.4;
}

.message-timestamp::before,
.message-timestamp::after {
  content: '';
  display: inline-block;
  width: 40px;
  height: 1px;
  background-color: #d0d0d0;
  margin: 0 8px;
  vertical-align: middle;
}

.message-wrapper {
  display: flex;
  margin-bottom: 0;
  width: 100%;
  align-items: flex-start;
}

.user-message {
  flex-direction: row-reverse;
}

.avatar {
  margin: 0 8px;
  flex-shrink: 0;
}

.avatar >>> .ant-avatar {
  box-shadow: none;
}

.message-content {
  max-width: 65%;
  display: flex;
  flex-direction: column;
  min-width: 40px;
}

.message-bubble {
  padding: 9px 12px;
  border-radius: 4px;
  position: relative;
  word-break: break-word;
  width: auto;
  display: inline-block;
  min-height: 0;
  height: auto;
  line-height: 1.5;
  box-sizing: border-box;
  font-size: 15px;
  transition: all 0.1s ease;
}

.message-bubble.clickable {
  cursor: pointer;
  transition: all 0.2s ease;
}

.user-message .message-bubble {
  background: #95ec69;
  color: #000;
  box-shadow: none;
}

.user-message .message-bubble.clickable:hover {
  background: #8de65b;
}

.ai-message .message-bubble {
  background-color: #fff;
  color: #000;
  box-shadow: none;
  border: none;
}

.ai-message .message-bubble.clickable:hover {
  background-color: #f5f5f5;
}

.message-text {
  white-space: pre-line;
  word-break: break-word;
  min-width: 20px;
  min-height: 0;
  height: auto;
  padding: 0;
  margin: 0;
  line-height: 1.4;
  font-size: 14px;
  letter-spacing: 0.3px;
}

.stt-message {
  color: #333;
}

.tts-message {
  color: #333;
}

.system-message {
  color: #666;
  font-style: italic;
}

.audio-message {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 120px;
}

.audio-duration {
  font-size: 12px;
  color: #666;
  font-weight: 500;
}

.audio-wave {
  display: flex;
  align-items: center;
  gap: 3px;
}

.wave-bar {
  width: 3px;
  height: 16px;
  background-color: currentColor;
  opacity: 0.6;
  border-radius: 2px;
  transition: all 0.3s ease;
}

.wave-bar.active {
  animation: sound-wave 1.2s infinite ease-in-out;
  opacity: 1;
}

@keyframes sound-wave {
  0%, 100% {
    height: 6px;
    transform: scaleY(0.4);
  }
  50% {
    height: 20px;
    transform: scaleY(1);
  }
}

.wave-bar:nth-child(2).active {
  animation-delay: 0.15s;
}

.wave-bar:nth-child(3).active {
  animation-delay: 0.3s;
}

.wave-bar:nth-child(4).active {
  animation-delay: 0.45s;
}

.loading-indicator {
  margin-top: 6px;
  align-self: flex-start;
}

.user-message .loading-indicator {
  align-self: flex-end;
}

.loading-indicator >>> .ant-spin-dot {
  color: #95ec69;
}

.chat-input-area {
  padding: 8px 12px;
  background: #f7f7f7;
  border-top: 1px solid #d6d6d6;
  box-shadow: none;
}

.text-input-wrapper {
  display: flex;
  width: 100%;
  align-items: flex-end;
  gap: 8px;
  padding: 0;
  background: transparent;
  border-radius: 0;
  border: none;
  box-shadow: none;
  transition: all 0.2s ease;
}

.text-input-wrapper >>> .ant-input {
  flex: 1;
  border: 1px solid #d6d6d6;
  border-radius: 6px;
  padding: 7px 12px;
  background-color: #fff;
  font-size: 15px;
  line-height: 20px;
  height: 36px;
  min-height: 36px;
  max-height: 120px;
  resize: none;
  outline: none;
  box-shadow: none;
  transition: all 0.2s ease;
}

.text-input-wrapper >>> .ant-input:focus {
  border-color: #07c160;
  box-shadow: none;
  outline: none;
}

.text-input-wrapper >>> .ant-input::placeholder {
  color: #999;
  font-size: 15px;
}

/* 使用 >>> 确保样式穿透 */
.text-input-wrapper >>> .ant-input {
  height: 36px !important;
  line-height: 20px !important;
}

.send-button {
  padding: 8px 20px;
  height: 36px;
  border-radius: 4px;
  background: #07c160;
  border: none;
  box-shadow: none;
  transition: all 0.2s ease;
  cursor: pointer;
  font-size: 15px;
  color: #fff;
  font-weight: 400;
  white-space: nowrap;
  flex-shrink: 0;
}

.send-button:hover:not(:disabled) {
  background: #06ad56;
}

.send-button:disabled {
  background: #d9d9d9;
  color: #999;
  cursor: not-allowed;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .chat-input-area {
    padding: 8px 12px;
  }

  .text-input-wrapper {
    gap: 8px;
  }

  .send-button {
    padding: 8px 16px;
    height: 34px;
    font-size: 14px;
  }

  .text-input-wrapper >>> .ant-input {
    padding: 6px 10px;
    height: 34px;
    min-height: 34px;
    line-height: 20px;
    font-size: 14px;
  }

  .text-input-wrapper >>> .ant-input {
    height: 34px !important;
    line-height: 20px !important;
  }
}

@media (max-width: 480px) {
  .chat-input-area {
    padding: 8px 10px;
  }

  .send-button {
    padding: 6px 14px;
    height: 32px;
    font-size: 13px;
  }

  .text-input-wrapper >>> .ant-input {
    padding: 5px 10px;
    height: 32px;
    min-height: 32px;
    line-height: 20px;
    font-size: 13px;
  }

  .text-input-wrapper >>> .ant-input {
    height: 32px !important;
    line-height: 20px !important;
  }
}
</style>