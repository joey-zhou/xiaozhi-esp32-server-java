<template>
  <div class="chat-container">
    <!-- 聊天头部 -->
    <div class="chat-header">
      <!-- 左侧连接按钮 -->
      <div class="header-left">
        <a-button 
          type="text" 
          class="connection-btn" 
          @click="toggleConnection" 
          :disabled="localConnectionStatus === '正在连接...'"
        >
          <a-icon 
            :type="localIsConnected ? 'disconnect' : 'link'" 
            :style="{ color: localIsConnected ? '#52c41a' : '#ff4d4f' }"
          />
        </a-button>
      </div>
      
      <div class="header-title">
        小智助手
        <a-tag :color="connectionStatusColor">{{ localConnectionStatus }}</a-tag>
      </div>
      
      <div class="header-right">
        <a-dropdown>
          <template #overlay>
            <a-menu>
              <a-menu-item key="1" @click="showServerConfig = true">
                <a-icon type="setting" />
                服务器设置
              </a-menu-item>
              <a-menu-item key="2" @click="showDebugPanel = !showDebugPanel">
                <a-icon type="bug" />
                调试面板
              </a-menu-item>
              <a-menu-item key="3">
                <a-popconfirm
                  title="确定要清空所有对话记录吗？"
                  ok-text="确定"
                  cancel-text="取消"
                  placement="leftBottom"
                  :overlay-style="{ maxWidth: '300px' }"
                  @confirm="handleClearMessages"
                >
                  <div style="display: flex; align-items: center; gap: 8px;">
                    <a-icon type="delete" />
                    <span>清空聊天</span>
                  </div>
                </a-popconfirm>
              </a-menu-item>
            </a-menu>
          </template>
          <a-button type="text">
            <a-icon type="more" />
          </a-button>
        </a-dropdown>
      </div>
    </div>

    <!-- 使用ChatComponent替换原来的聊天内容和输入区域 -->
    <ChatComponent
      ref="chatComponentRef"
      :message-list="messages"
      :show-input="true"
      :show-voice-toggle="false"
      :user-avatar="userAvatar"
      :ai-avatar="aiAvatar"
      :input-placeholder="'输入消息...'"
      :empty-text="'暂无对话，开始聊天吧'"
      :is-connected-prop="localIsConnected"
    />

    <!-- 连接提示 -->
    <a-alert
      v-if="connectionAlert.show"
      :message="connectionAlert.title"
      :description="connectionAlert.message"
      :type="connectionAlert.type"
      class="connection-alert"
      closable
      @close="connectionAlert.show = false"
    >
      <template slot="action">
        <div class="connection-actions">
          <a-button size="small" @click="connect">连接</a-button>
          <a-button size="small" type="primary" @click="showServerConfig = true">配置服务器</a-button>
        </div>
      </template>
    </a-alert>

    <!-- 服务器配置对话框 -->
    <a-modal
      v-model="showServerConfig"
      title="服务器配置"
      @ok="saveServerConfig"
      @cancel="initTempServerConfig"
      okText="保存"
      cancelText="取消"
    >
      <a-form :form="form" layout="vertical">
        <a-form-item label="服务器地址">
          <a-input v-model="tempServerConfig.url" placeholder="ws://服务器地址:端口/路径" />
        </a-form-item>
        <a-form-item label="设备ID">
          <a-input v-model="tempServerConfig.deviceId" placeholder="设备唯一标识" />
        </a-form-item>
        <a-form-item label="设备名称">
          <a-input v-model="tempServerConfig.deviceName" placeholder="设备名称" />
        </a-form-item>
        <a-form-item label="认证令牌">
          <a-input v-model="tempServerConfig.token" placeholder="认证令牌" />
        </a-form-item>
      </a-form>

      <!-- 自动连接设置 -->
      <a-form-item label="自动连接">
        <a-switch
          v-model="autoConnectSwitch"
          checked-children="开"
          un-checked-children="关"
        />
        <span style="margin-left: 8px; color: #666;">登录后自动连接WebSocket</span>
      </a-form-item>
    </a-modal>

    <!-- 调试面板 -->
    <div class="debug-panel-container" :class="{ show: showDebugPanel }">
      <div class="debug-panel-header">
        <span>调试面板</span>
        <a-button type="text" @click="showDebugPanel = false">
          <a-icon type="close" />
        </a-button>
      </div>
      <div class="debug-panel">
        <a-space direction="vertical" style="width: 100%">
          <a-card size="small" title="连接状态">
            <p><strong>状态:</strong> {{ localConnectionStatus }}</p>
            <p v-if="connectionTime"><strong>连接时间:</strong> {{ formatTimestamp(connectionTime) }}</p>
            <p v-if="sessionId"><strong>会话ID:</strong> {{ sessionId }}</p>
            <a-space>
              <a-button
                size="small"
                :type="localIsConnected ? 'default' : 'primary'"
                @click="toggleConnection"
                :disabled="localConnectionStatus === '正在连接...'"
              >
                <a-icon :type="localIsConnected ? 'disconnect' : 'link'" />
                {{ localIsConnected ? '断开' : '连接' }}
              </a-button>
              <a-button size="small" @click="handleReconnect" :disabled="localIsConnected">重连</a-button>
              <a-button size="small" @click="handleStopReconnect" type="danger">停止重连</a-button>
            </a-space>
          </a-card>

          <a-card size="small" title="日志">
            <div class="log-container" ref="logContainerRef">
              <div v-for="(entry, index) in logs" :key="index" class="log-entry" :class="`log-${entry.type}`">
                <span class="log-time">{{ formatLogTime(entry.time) }}</span>
                <span class="log-message">{{ entry.message }}</span>
              </div>
            </div>
          </a-card>

          <a-card size="small" title="音频可视化">
            <canvas ref="audioVisualizerRef" class="audio-visualizer"></canvas>
          </a-card>
        </a-space>
      </div>
    </div>

    <!-- 添加一个悬浮的重连按钮，当连接断开时显示 -->
    <div v-if="!localIsConnected && !connectionAlert.show" class="floating-reconnect-btn">
      <a-button type="primary" shape="circle" @click="connect" title="重新连接">
        <a-icon type="reload" />
      </a-button>
    </div>
  </div>
</template>

<script>
import {
  // WebSocket相关
  startDirectRecording,
  stopDirectRecording,
  reconnectToServer,
  stopAutoReconnect,

  // 日志相关
  log,
  getLogs,

  // 消息相关
  messages,
  clearMessages
} from '@/services/websocketService';

import {
  initAudio,
  handleBinaryMessage,
  getAudioState
} from '@/services/audioService';

// 导入ChatComponent和mixin
import ChatComponent from '@/components/ChatComponent';
import websocketMixin from '@/mixins/websocketMixin';
import { getResourceUrl } from '@/services/axios';

export default {
  name: 'Chat',
  mixins: [websocketMixin],
  components: {
    ChatComponent
  },
  data() {
    return {
      // 状态变量
      isVoiceMode: false, // 默认为文字输入模式
      messages: messages, // 使用websocketService中的消息列表
      logs: [],
      showDebugPanel: false,
      showServerConfig: false,
      connectionAlert: {
        show: false,
        title: '未连接到服务器',
        message: '请配置并连接到小智服务器',
        type: 'info'
      },
      form: this.$form.createForm(this),
      // 临时服务器配置（用于编辑）
      tempServerConfig: {}
    };
  },
  computed: {
    // 本地别名（为了兼容现有模板）
    localIsConnected() {
      return this.wsIsConnected;
    },
    localConnectionStatus() {
      return this.wsConnectionStatus;
    },
    connectionTime() {
      return this.$store.getters.WS_CONNECTION_TIME;
    },
    sessionId() {
      return this.$store.getters.WS_SESSION_ID;
    },

    // 用户头像
    userAvatar() {
      const userInfo = this.$store.getters.USER_INFO;
      if (userInfo && userInfo.avatar) {
        return getResourceUrl(userInfo.avatar);
      }
      return '';
    },

    // 自动连接开关的双向绑定
    autoConnectSwitch: {
      get() {
        return this.wsAutoConnect;
      },
      set(value) {
        this.$store.commit('SET_WS_AUTO_CONNECT', value);
      }
    },

    // 使用计算属性获取最新的连接状态
    connectionStatusColor() {
      if (this.localIsConnected) return 'green';
      if (this.localConnectionStatus && (
          this.localConnectionStatus.includes('错误') ||
          this.localConnectionStatus.includes('失败')
      )) return 'red';
      if (this.localConnectionStatus === '正在连接...') return 'blue';
      return 'red';
    }
  },
  watch: {
    // 监听连接状态变化
    localIsConnected: {
      handler(newValue) {
        if (newValue) {
          // 连接成功
          this.connectionAlert.show = false;
        } else {
          // 连接断开，但不显示警告（全局管理会处理重连）
          this.connectionAlert.show = false;
        }
      },
      immediate: true
    },

    // 监听连接状态文本变化，用于错误提示
    localConnectionStatus: {
      handler(newValue) {
        if (newValue && (newValue.includes('错误') || newValue.includes('失败'))) {
          this.connectionAlert.show = true;
          this.connectionAlert.title = '连接问题';
          this.connectionAlert.message = newValue;
          this.connectionAlert.type = 'error';
        } else if (newValue === '已连接') {
          this.connectionAlert.show = false;
        }
      },
      immediate: true
    },

    // 监听配置对话框打开
    showServerConfig: {
      handler(newValue) {
        if (newValue) {
          this.initTempServerConfig();
        }
      }
    }
  },
  mounted() {
    // 初始化音频可视化
    this.initAudioVisualizer();

    // 加载日志
    this.logs = getLogs();

    // 设置当前活动的音频处理器已通过mixin处理

    // 初始化音频服务
    this.initAudioService();

    // 监听窗口大小变化
    window.addEventListener('resize', this.initAudioVisualizer);

    // 监听日志更新
    this.$watch(
      () => getLogs(),
      (newLogs) => {
        this.logs = newLogs;

        // 滚动日志到底部
        this.$nextTick(() => {
          if (this.$refs.logContainerRef) {
            this.$refs.logContainerRef.scrollTop = this.$refs.logContainerRef.scrollHeight;
          }
        });
      },
      { deep: true }
    );

    // 初始化临时配置
    this.initTempServerConfig();
  },
  beforeDestroy() {
    // 清除窗口事件监听
    window.removeEventListener('resize', this.initAudioVisualizer);

    // WebSocket处理器清理由mixin处理
  },
  methods: {
    // 初始化临时配置
    initTempServerConfig() {
      this.tempServerConfig = { ...this.wsServerConfig };
    },

    // 切换连接状态
    async toggleConnection() {
      if (this.localIsConnected) {
        await this.disconnectFromServer();
      } else {
        await this.connect();
      }
    },

    // 初始化音频服务
    async initAudioService() {
      try {
        await initAudio();
      } catch (error) {
        log(`音频初始化失败: ${error.message}`, 'error');
      }
    },

    // 初始化音频可视化
    initAudioVisualizer() {
      if (!this.$refs.audioVisualizerRef) return;

      const canvas = this.$refs.audioVisualizerRef;
      canvas.width = canvas.clientWidth;
      canvas.height = canvas.clientHeight;

      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.fillStyle = '#fafafa';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
      }
    },

    // 处理WebSocket消息（由mixin调用）
    handleWebSocketMessage(data) {
      if (!data || !data.type) return;
      // 处理消息逻辑保持不变
    },

    // 处理二进制音频消息
    async handleBinaryAudioMessage(data) {
      try {
        // 调用ChatComponent中的处理方法
        if (this.$refs.chatComponentRef) {
          return await this.$refs.chatComponentRef.handleBinaryAudioMessage(data);
        } else {
          // 如果ChatComponent还没准备好，则使用audioService中的方法
          return await handleBinaryMessage(data);
        }
      } catch (error) {
        log(`处理二进制音频消息失败: ${error.message}`, 'error');
        return false;
      }
    },

    // 连接到服务器（使用mixin中的方法）
    async connect() {
      return await this.connectWebSocket();
    },

    // 显示连接错误
    showConnectionError(title, message) {
      this.connectionAlert.show = true;
      this.connectionAlert.title = title;
      this.connectionAlert.message = message;
      this.connectionAlert.type = 'error';
    },

    // 处理录音开始
    handleRecordingStart() {
      log('录音开始', 'info');
    },

    // 处理录音结束
    handleRecordingStop() {
      log('录音结束', 'info');
    },

    // 处理录音错误
    handleRecordingError(error) {
      log(`录音错误: ${error.message}`, 'error');
    },

    // 处理输入模式变化
    handleModeChange(isVoiceMode) {
      this.isVoiceMode = isVoiceMode;
      log(`切换到${isVoiceMode ? '语音' : '文字'}输入模式`, 'info');
    },

    // 保存服务器配置
    async saveServerConfig() {
      // 更新store中的配置
      this.$store.commit('SET_WS_SERVER_CONFIG', this.tempServerConfig);

      this.showServerConfig = false;

      // 如果已连接但配置改变，提示重新连接
      if (this.localIsConnected) {
        this.$message.info('配置已保存，需要重新连接才能生效');
        // 先断开再重新连接
        await this.disconnectWebSocket();
        setTimeout(() => {
          this.connectWebSocket();
        }, 1000);
      } else {
        // 尝试连接
        this.connectWebSocket();
      }
    },

    // 格式化时间戳
    formatTimestamp(timestamp) {
      if (!timestamp) return '';

      const date = timestamp instanceof Date ? timestamp : new Date(timestamp);
      return date.toLocaleString();
    },

    // 格式化日志时间
    formatLogTime(time) {
      if (!time) return '';

      const date = time instanceof Date ? time : new Date(time);
      return date.toLocaleTimeString();
    },

    // 清空消息
    handleClearMessages() {
      clearMessages();
      this.$message.success('已清空对话记录');
    },

    // 连接到服务器（调用websocketService）
    connectToServer() {
      return this.connectWebSocket();
    },

    // 断开连接（使用mixin中的方法）
    async disconnectFromServer() {
      return await this.disconnectWebSocket();
    },

    // 手动重连
    async handleReconnect() {
      try {
        this.$message.info('正在重连...');
        const success = await reconnectToServer();
        if (success) {
          this.$message.success('重连成功');
        } else {
          this.$message.error('重连失败');
        }
      } catch (error) {
        this.$message.error(`重连失败: ${error.message}`);
      }
    },

    // 停止自动重连
    handleStopReconnect() {
      try {
        stopAutoReconnect();
        this.$message.info('已停止自动重连');
      } catch (error) {
        this.$message.error(`停止重连失败: ${error.message}`);
      }
    },
  }
};
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 80vh;
  background-color: #f5f5f5;
  position: relative;
  max-width: 800px;
  margin: 0 auto;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  border-radius: 0;
  overflow: hidden;
  border: 1px solid #d6d6d6;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f7f7f7;
  border-bottom: 1px solid #d6d6d6;
  box-shadow: none;
  z-index: 10;
}

.header-left {
  flex: 0 0 auto;
  width: 40px;
  display: flex;
  justify-content: flex-start;
}

.header-title {
  flex: 1;
  text-align: center;
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #333;
  letter-spacing: 0.5px;
}

.header-title .ant-tag {
  margin-left: 10px;
  font-size: 11px;
  padding: 2px 8px;
  height: 20px;
  line-height: 16px;
  border-radius: 4px;
  font-weight: 500;
}

.header-right {
  flex: 0 0 auto;
  width: 40px;
  display: flex;
  justify-content: flex-end;
}

/* 连接按钮样式 */
.connection-btn {
  height: 32px;
  width: 32px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background: transparent;
  border: none;
  transition: all 0.2s ease;
}

.connection-btn:hover {
  background: rgba(0, 0, 0, 0.05);
}

.connection-btn .anticon {
  font-size: 18px;
}

.connection-alert {
  position: fixed;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  width: 90%;
  max-width: 500px;
  z-index: 100;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-radius: 6px;
}

.connection-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.connection-actions .ant-btn {
  border-radius: 4px;
  font-weight: 400;
  transition: all 0.2s ease;
}

.connection-actions .ant-btn:hover {
  opacity: 0.9;
}

/* 悬浮重连按钮 */
.floating-reconnect-btn {
  position: fixed;
  bottom: 30px;
  right: 30px;
  z-index: 90;
}

.floating-reconnect-btn .ant-btn {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #07c160;
  border: none;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  transition: all 0.2s ease;
}

.floating-reconnect-btn .ant-btn:hover {
  background: #06ad56;
}

.floating-reconnect-btn .anticon {
  font-size: 20px;
  color: #fff;
}

.debug-panel-container {
  position: fixed;
  top: 0;
  right: -350px;
  width: 350px;
  height: 100vh;
  background-color: #fff;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.15);
  transition: right 0.3s;
  z-index: 1000;
  display: flex;
  flex-direction: column;
}

.debug-panel-container.show {
  right: 0;
}

.debug-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e8e8e8;
  font-weight: 500;
}

.debug-panel {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.log-container {
  height: 200px;
  overflow-y: auto;
  background-color: #f5f5f5;
  border-radius: 4px;
  padding: 8px;
  font-family: monospace;
  font-size: 12px;
}

.log-entry {
  margin-bottom: 4px;
  line-height: 1.4;
}

.log-time {
  color: #888;
  margin-right: 6px;
}

.log-info {
  color: #333;
}

.log-error {
  color: #f5222d;
}

.log-warning {
  color: #faad14;
}

.log-success {
  color: #52c41a;
}

.log-debug {
  color: #8c8c8c;
}

.audio-visualizer {
  width: 100%;
  height: 100px;
  background-color: #fafafa;
  border-radius: 4px;
}

/* 美化确认提示框 */
.chat-container >>> .ant-popover.ant-popconfirm {
  .ant-popover-inner-content {
    background: #fff;
    border-radius: 8px;
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
    border: 1px solid #e8e8e8;
  }

  .ant-popover-message {
    padding: 12px 16px 8px;
    color: #333;
    font-size: 14px;
    line-height: 1.5;
  }

  .ant-popover-message-icon {
    color: #faad14;
    font-size: 16px;
  }

  .ant-popover-message-title {
    color: #333;
    font-weight: 500;
  }

  .ant-popover-buttons {
    padding: 8px 16px 12px;
    gap: 8px;
  }

  .ant-btn {
    border-radius: 6px;
    font-size: 12px;
    height: 28px;
    padding: 0 12px;
    font-weight: 500;
    transition: all 0.2s ease;
  }

  .ant-btn-primary {
    background: linear-gradient(135deg, #ff6b6b 0%, #ff5252 100%);
    border: none;
    color: #fff;
    box-shadow: 0 2px 4px rgba(255, 107, 107, 0.3);
  }

  .ant-btn-primary:hover {
    background: linear-gradient(135deg, #ff5252 0%, #e53935 100%);
    transform: translateY(-1px);
    box-shadow: 0 4px 8px rgba(255, 107, 107, 0.4);
  }

  .ant-btn-default {
    background: #f5f5f5;
    border: 1px solid #d9d9d9;
    color: #666;
  }

  .ant-btn-default:hover {
    background: #e8e8e8;
    border-color: #b3b3b3;
    color: #333;
    transform: translateY(-1px);
  }
}
</style>