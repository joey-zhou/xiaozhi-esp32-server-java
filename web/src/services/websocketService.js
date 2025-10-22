// websocketService.js - 统一的WebSocket、消息和日志管理服务

// 为了避免循环依赖，我们将store引用延迟到需要时获取
let storeInstance = null;

// 获取store实例
function getStore() {
  if (!storeInstance) {
    try {
      // 动态导入store，避免循环依赖
      const store = require('@/store').default;
      storeInstance = store;
    } catch (error) {
      console.warn('无法获取store实例:', error);
    }
  }
  return storeInstance;
}

// 日志相关
// =============================
const LOG_LEVELS = {
  debug: 0,
  info: 1,
  success: 2,
  warning: 3,
  error: 4
};

let currentLogLevel = LOG_LEVELS.debug;
let logHistory = [];
const MAX_LOG_HISTORY = 500;

// 记录日志
export function log(message, type = 'info') {
  if (LOG_LEVELS[type] < currentLogLevel) return;

  const entry = {
    message,
    type,
    time: new Date()
  };

  logHistory.push(entry);

  if (logHistory.length > MAX_LOG_HISTORY) {
    logHistory = logHistory.slice(-MAX_LOG_HISTORY);
  }

  switch (type) {
    case 'error': console.error(message); break;
    case 'warning': console.warn(message); break;
    case 'success': console.log('%c' + message, 'color: green'); break;
    case 'debug': console.debug(message); break;
    default: console.log(message);
  }

  return entry;
}

// 获取日志历史
export function getLogs() {
  return [...logHistory];
}

// 清除日志历史
export function clearLogs() {
  logHistory = [];
  return true;
}

// 设置日志级别
export function setLogLevel(level) {
  if (LOG_LEVELS[level] !== undefined) {
    currentLogLevel = LOG_LEVELS[level];
    return true;
  }
  return false;
}

// 消息管理相关
// =============================
export const messages = [];

// 打字机效果相关
// =============================
let typewriterTimer = null;
let typewriterQueue = []; // 待打字的文本队列
let isTyping = false; // 是否正在打字
const TYPING_SPEED = 50; // 每个字的显示间隔（毫秒）

// 当前正在构建的AI回复消息
let currentAIMessage = null;

// 添加消息
export function addMessage(message) {
  if (!message.content) return null;

  const newMessage = {
    id: message.id || `msg_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
    content: String(message.content).trim(),
    type: message.type || 'stt',
    isUser: !!message.isUser,
    timestamp: message.timestamp || new Date(),
    isLoading: !!message.isLoading
  };

  messages.push(newMessage);

  log(`添加${newMessage.isUser ? '用户' : 'AI'}消息: ${newMessage.content.substring(0, 50)}${newMessage.content.length > 50 ? '...' : ''}`, 'debug');

  return newMessage;
}

// 更新消息 - 内部使用
function updateMessage(id, updates) {
  const index = messages.findIndex(msg => msg.id === id);
  if (index === -1) {
    log(`未找到要更新的消息: ${id}`, 'warning');
    return null;
  }

  const updatedMessage = {
    ...messages[index],
    ...updates
  };

  messages[index] = updatedMessage;

  log(`更新消息 ${id}: ${JSON.stringify(updates)}`, 'debug');

  return updatedMessage;
}

// 添加语音转文本消息 - 内部使用
function addSTTMessage(content) {
  return addMessage({
    content,
    type: 'stt',
    isUser: true
  });
}

// 添加文本转语音消息 - 内部使用
function addTTSMessage(content) {
  return addMessage({
    content,
    type: 'tts',
    isUser: false
  });
}

// 添加音频消息 - 内部使用
function addAudioMessage(options) {
  const message = {
    content: options.content || '语音消息',
    type: 'audio',
    isUser: !!options.isUser,
    duration: options.duration || '0:00',
    audioData: options.audioData
  };

  return addMessage(message);
}

// 删除消息 - 内部使用
function deleteMessage(id) {
  const index = messages.findIndex(msg => msg.id === id);
  if (index === -1) return false;

  messages.splice(index, 1);
  log(`删除消息: ${id}`, 'debug');

  return true;
}

// 清空所有消息
export function clearMessages() {
  // 使用splice方法清空数组，Vue可以检测到这种变化
  messages.splice(0, messages.length);
  currentAIMessage = null; // 重置当前AI消息
  log('清空所有消息', 'info');

  return true;
}

// 打字机效果：逐字显示文本
function startTypewriter(text) {
  // 将文本添加到队列
  typewriterQueue.push(text);
  
  // 如果没有在打字，启动打字机
  if (!isTyping) {
    processTypewriterQueue();
  }
}

// 处理打字机队列
function processTypewriterQueue() {
  if (typewriterQueue.length === 0) {
    isTyping = false;
    return;
  }
  
  isTyping = true;
  const text = typewriterQueue.shift();
  const chars = Array.from(text); // 支持 emoji 和多字节字符
  let currentIndex = 0;
  
  // 如果是第一次打字，创建消息
  if (!currentAIMessage) {
    currentAIMessage = {
      id: `msg_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
      content: '',
      type: 'tts',
      isUser: false,
      timestamp: new Date(),
      isLoading: false
    };
    messages.push(currentAIMessage);
    log(`📝 创建新的AI回复消息 (ID: ${currentAIMessage.id})`, 'info');
  }
  
  // 逐字添加
  const typeNextChar = () => {
    if (currentIndex < chars.length) {
      currentAIMessage.content += chars[currentIndex];
      currentIndex++;
      
      // 触发Vue响应式更新
      const index = messages.findIndex(msg => msg.id === currentAIMessage.id);
      if (index !== -1) {
        messages.splice(index, 1, { ...currentAIMessage });
      }
      
      typewriterTimer = setTimeout(typeNextChar, TYPING_SPEED);
    } else {
      // 当前文本打完，处理下一个
      log(`✅ 完成打字: "${text}"`, 'debug');
      processTypewriterQueue();
    }
  };
  
  typeNextChar();
}

// 停止打字机效果
function stopTypewriter() {
  if (typewriterTimer) {
    clearTimeout(typewriterTimer);
    typewriterTimer = null;
  }
  isTyping = false;
  typewriterQueue = [];
}

// WebSocket连接相关
// =============================
let webSocket = null;
let isConnecting = false;
let messageHandlers = [];
let reconnectTimer = null;
let reconnectAttempts = 0;
let maxReconnectAttempts = 5;
let reconnectDelay = 2000; // 初始重连延迟2秒

// 内部状态变量 - 不导出，通过store管理
let connectionStatus = '未连接';
let connectionTime = null;
let sessionId = null;
let isConnected = false;

// 添加状态变更回调
let statusChangeCallbacks = [];

// 注册状态变更回调
export function registerStatusChangeCallback(callback) {
  if (typeof callback === 'function' && !statusChangeCallbacks.includes(callback)) {
    statusChangeCallbacks.push(callback);
    return true;
  }
  return false;
}

// 移除状态变更回调
export function unregisterStatusChangeCallback(callback) {
  const index = statusChangeCallbacks.indexOf(callback);
  if (index !== -1) {
    statusChangeCallbacks.splice(index, 1);
    return true;
  }
  return false;
}

// 通知状态变更
function notifyStatusChange() {
  const status = {
    isConnected,
    connectionStatus,
    connectionTime,
    sessionId
  };

  // 同步状态到Vue store
  try {
    const store = getStore();
    if (store) {
      store.commit('SET_WS_CONNECTION_STATUS', {
        isConnected,
        status: connectionStatus,
        connectionTime,
        sessionId
      });
    }
  } catch (error) {
    log(`同步状态到store失败: ${error.message}`, 'error');
  }

  // 通知所有注册的回调
  statusChangeCallbacks.forEach(callback => {
    try {
      callback(status);
    } catch (error) {
      log(`状态变更回调执行错误: ${error.message}`, 'error');
    }
  });
}

// 注册消息处理函数
export function registerMessageHandler(handler) {
  if (typeof handler === 'function' && !messageHandlers.includes(handler)) {
    messageHandlers.push(handler);
    return true;
  }
  return false;
}

// 移除消息处理函数
export function unregisterMessageHandler(handler) {
  const index = messageHandlers.indexOf(handler);
  if (index !== -1) {
    messageHandlers.splice(index, 1);
    return true;
  }
  return false;
}

// 连接到服务器
export async function connectToServer(config) {
  if (webSocket && webSocket.readyState === WebSocket.OPEN) {
    log('WebSocket已连接', 'info');
    return true;
  }

  if (isConnecting) {
    log('WebSocket正在连接中...', 'info');
    return false;
  }

  try {
    isConnecting = true;
    connectionStatus = '正在连接...';

    // 清除之前的重连计时器
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }

    // 关闭现有连接
    if (webSocket) {
      try {
        webSocket.close();
      } catch (e) {
        // 忽略关闭错误
      }
    }

    // 构建连接URL
    let url = config.url;
    if (!url.endsWith('/')) {
      url += '/';
    }

    // 添加设备ID和名称作为查询参数
    const params = new URLSearchParams();
    if (config.deviceId) {
      params.append('device-id', config.deviceId);
    }
    if (config.macAddress) {
      params.append('mac_address', config.deviceName);
    }
    if (config.token) {
      params.append('token', config.token);
    }

    const queryString = params.toString();
    if (queryString) {
      url += '?' + queryString;
    }

    log(`正在连接到: ${url}`, 'info');

    // 创建WebSocket连接
    webSocket = new WebSocket(url);

    // 设置二进制类型为ArrayBuffer
    webSocket.binaryType = 'arraybuffer';

    // 连接打开事件
    webSocket.onopen = () => {
      isConnecting = false;
      isConnected = true;
      connectionStatus = '已连接';
      connectionTime = new Date();
      reconnectAttempts = 0;
      log('WebSocket连接已建立', 'success');
      notifyStatusChange();
    };

    // 接收消息事件
    webSocket.onmessage = (event) => {
      handleWebSocketMessage(event);
    };

    // 连接关闭事件
    webSocket.onclose = (event) => {
      isConnecting = false;
      isConnected = false;

      if (event.wasClean) {
        connectionStatus = '已断开';
        log(`WebSocket连接已关闭: 代码=${event.code}, 原因=${event.reason}`, 'info');
      } else {
        connectionStatus = '连接已断开';
        log('WebSocket连接意外断开', 'error');

        // 尝试重新连接
        scheduleReconnect(config);
      }

      notifyStatusChange();
    };

    // 连接错误事件
    webSocket.onerror = (error) => {
      isConnecting = false;
      isConnected = false;
      connectionStatus = '连接错误';
      log('WebSocket连接错误', 'error');
      notifyStatusChange();

      // 错误时不立即重连，让onclose处理
    };

    // 等待连接完成或超时
    return new Promise((resolve) => {
      // 连接超时处理
      const timeoutId = setTimeout(() => {
        if (!isConnected) {
          log('WebSocket连接超时', 'error');
          isConnecting = false;
          connectionStatus = '连接超时';

          try {
            webSocket.close();
          } catch (e) {
            // 忽略关闭错误
          }

          resolve(false);
        }
      }, 5000); // 5秒超时

      // 监听连接状态变化
      const checkConnected = () => {
        if (isConnected) {
          clearTimeout(timeoutId);
          resolve(true);
        } else if (connectionStatus.includes('错误') || connectionStatus.includes('超时')) {
            clearTimeout(timeoutId);
            resolve(false);
          } else {
            setTimeout(checkConnected, 100);
          }
      };

      checkConnected();
    });
  } catch (error) {
    isConnecting = false;
    isConnected = false;
    connectionStatus = '连接失败';
    log(`WebSocket连接失败: ${error.message}`, 'error');
    notifyStatusChange();
    return false;
  }
}

// 安排重新连接
function scheduleReconnect(config) {
  if (reconnectAttempts >= maxReconnectAttempts) {
    log(`已达到最大重连次数(${maxReconnectAttempts})，停止重连`, 'warning');
    connectionStatus = '重连失败';
    notifyStatusChange();
    return;
  }

  // 使用指数退避策略
  const delay = reconnectDelay * Math.pow(1.5, reconnectAttempts);

  log(`计划在${delay / 1000}秒后重新连接(尝试${reconnectAttempts + 1}/${maxReconnectAttempts})`, 'info');
  connectionStatus = `${Math.ceil(delay / 1000)}秒后重连...`;
  notifyStatusChange();

  reconnectTimer = setTimeout(() => {
    reconnectAttempts++;
    connectToServer(config);
  }, delay);
}

// 处理WebSocket消息
function handleWebSocketMessage(event) {
  try {
    // 检查是否是二进制数据
    if (event.data instanceof ArrayBuffer || event.data instanceof Blob) {
      // 处理二进制音频数据
      // 这里我们需要从audioService导入handleBinaryMessage
      // 但为了避免循环依赖，我们在Chat.vue中处理这个问题
      if (typeof window.currentAudioHandler === 'function') {
        window.currentAudioHandler(event.data);
      } else {
        log('未找到二进制音频处理函数', 'warning');
      }
      return;
    }

    // 处理文本数据
    const data = JSON.parse(event.data);

    // 记录会话ID
    if (data.session_id && !sessionId) {
      sessionId = data.session_id;
      log(`会话ID: ${sessionId}`, 'info');
      notifyStatusChange();
    }

    // 根据消息类型处理
    switch (data.type) {
      case 'stt':
        handleSTTMessage(data);
        break;
      case 'tts':
        handleTTSMessage(data);
        break;
      default:
        log(`收到未知类型的消息: ${data.type}`, 'warning');
    }

    // 调用所有注册的消息处理函数
    for (const handler of messageHandlers) {
      try {
        handler(data);
      } catch (error) {
        log(`消息处理函数执行错误: ${error.message}`, 'error');
      }
    }
  } catch (error) {
    log(`处理WebSocket消息出错: ${error.message}`, 'error');
  }
}

// 处理STT消息
function handleSTTMessage(data) {
  // 添加语音转文本消息
  addSTTMessage(data.text);
  log(`语音识别结果: ${data.text}`, 'info');
}

// 处理TTS消息
function handleTTSMessage(data) {
  if (data.state === 'start') {
    log('🎵 TTS开始，准备接收音频', 'info');
    
    // 重置打字机和当前AI消息
    stopTypewriter();
    currentAIMessage = null;
    
    // 通知音频服务准备接收新的音频流
    if (window.dispatchEvent) {
      window.dispatchEvent(new CustomEvent('audio-stream-start'));
    }
  } else if (data.state === 'sentence_start' && data.text) {
    // 将新句子加入打字机队列
    log(`📥 收到新句子: "${data.text}"`, 'info');
    startTypewriter(data.text);
  } else if (data.state === 'stop') {
    log('🛑 TTS结束，音频流结束', 'info');
    
    // 等待打字机完成后再清理
    const waitForTyping = () => {
      if (!isTyping && typewriterQueue.length === 0) {
        if (currentAIMessage) {
          log(`✅ AI回复完成，最终内容: "${currentAIMessage.content}"`, 'info');
          currentAIMessage = null;
        }
      } else {
        setTimeout(waitForTyping, 100);
      }
    };
    waitForTyping();
    
    // 通知音频服务流已结束
    if (window.dispatchEvent) {
      window.dispatchEvent(new CustomEvent('audio-stream-end'));
    }
  }
}

// 发送JSON消息
function sendJsonMessage(data) {
  if (!webSocket || webSocket.readyState !== WebSocket.OPEN) {
    log('WebSocket未连接，无法发送消息', 'error');
    return false;
  }

  try {
    const message = JSON.stringify(data);
    webSocket.send(message);
    return true;
  } catch (error) {
    log(`发送JSON消息失败: ${error.message}`, 'error');
    return false;
  }
}

// 发送文本消息
export function sendTextMessage(text) {
  if (!text || !webSocket || webSocket.readyState !== WebSocket.OPEN) {
    return false;
  }

  try {
    const message = {
      type: 'listen',
      state: 'text',
      text: text
    };

    return sendJsonMessage(message);
  } catch (error) {
    log(`发送文本消息失败: ${error.message}`, 'error');
    return false;
  }
}

// 开始直接录音
export async function startDirectRecording() {
  if (!webSocket || webSocket.readyState !== WebSocket.OPEN) {
    throw new Error('WebSocket未连接');
  }

  try {
    // 发送开始录音命令
    const startMessage = {
      type: 'stt',
      state: 'start'
    };

    sendJsonMessage(startMessage);
    log('已发送开始录音命令', 'info');

    return true;
  } catch (error) {
    log(`开始录音失败: ${error.message}`, 'error');
    throw error;
  }
}

// 停止直接录音
export async function stopDirectRecording() {
  if (!webSocket || webSocket.readyState !== WebSocket.OPEN) {
    throw new Error('WebSocket未连接');
  }

  try {
    // 发送停止录音命令
    const stopMessage = {
      type: 'stt',
      state: 'stop'
    };

    sendJsonMessage(stopMessage);
    log('已发送停止录音命令', 'info');

    return true;
  } catch (error) {
    log(`停止录音失败: ${error.message}`, 'error');
    throw error;
  }
}

export async function reconnectToServer() {
  try {
    log('手动触发重连...', 'info');
    
    // 先断开现有连接
    await disconnectFromServer();
    
    // 重置重连计数
    reconnectAttempts = 0;
    
    // 获取配置
    const store = getStore();
    if (!store) {
      throw new Error('无法获取store实例');
    }
    
    const config = store.getters.WS_SERVER_CONFIG;
    
    // 立即尝试重新连接
    return await connectToServer(config);
  } catch (error) {
    log(`手动重连失败: ${error.message}`, 'error');
    connectionStatus = '重连失败';
    notifyStatusChange();
    return false;
  }
}

// 停止自动重连
export function stopAutoReconnect() {
  try {
    // 清除重连计时器
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
      log('已停止自动重连', 'info');
    }
    
    // 重置重连尝试次数
    reconnectAttempts = 0;
    
    // 如果当前状态是某种"等待重连"状态，则更新为"已停止重连"
    if (connectionStatus.includes('重连')) {
      connectionStatus = '已停止重连';
      notifyStatusChange();
    }
    
    return true;
  } catch (error) {
    log(`停止自动重连失败: ${error.message}`, 'error');
    return false;
  }
}

// 断开连接
export function disconnectFromServer() {
  // 清除重连计时器
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  
  // 停止打字机效果
  stopTypewriter();

  if (!webSocket) {
    return true;
  }

  try {
    if (webSocket.readyState === WebSocket.OPEN) {
      webSocket.close(1000, '用户主动断开');
    }

    webSocket = null;
    isConnected = false;
    connectionStatus = '已断开';
    log('WebSocket连接已断开', 'info');
    notifyStatusChange();

    return true;
  } catch (error) {
    log(`断开WebSocket连接失败: ${error.message}`, 'error');
    return false;
  }
}

// 检查WebSocket是否已连接
export function isWebSocketConnected() {
  return webSocket && webSocket.readyState === WebSocket.OPEN;
}
