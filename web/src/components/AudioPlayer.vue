<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { PlayCircleOutlined, PauseCircleOutlined } from '@ant-design/icons-vue'
import WaveSurfer from 'wavesurfer.js'
import { getResourceUrl } from '@/utils/resource'
import { useEventBus } from '@vueuse/core'

interface Props {
  audioUrl: string
  autoPlay?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  autoPlay: false,
})

// 状态
const wavesurfer = ref<WaveSurfer | null>(null)
const isPlaying = ref(false)
const loading = ref(true)
const loadError = ref(false)
const useFallback = ref(false)
const playerId = ref('')
const waveformRef = ref<HTMLDivElement>()
const fallbackAudioRef = ref<HTMLAudioElement>()
const fallbackProgress = ref(0)
const fallbackDuration = ref('')
const fallbackCurrentTime = ref('')

// 使用 VueUse 的事件总线
const audioPlayBus = useEventBus<string>('audio-play')
const stopAllAudioBus = useEventBus<void>('stop-all-audio')

/**
 * 初始化 WaveSurfer
 */
function initWaveSurfer() {
  if (!waveformRef.value) {
    console.error('WaveSurfer 容器未找到')
    return
  }

  try {
    // 创建 wavesurfer 实例
    wavesurfer.value = WaveSurfer.create({
      container: waveformRef.value,
      waveColor: 'var(--ant-color-border)',
      progressColor: 'var(--ant-color-primary)',
      cursorColor: 'transparent',
      barWidth: 2,
      barRadius: 2,
      barGap: 1,
      height: 40,
      normalize: true,
    })
  } catch (error) {
    console.error('WaveSurfer 初始化失败:', error)
    loading.value = false
    loadError.value = true
    return
  }

  // 事件监听
  wavesurfer.value.on('ready', () => {
    loading.value = false
    if (props.autoPlay && wavesurfer.value) {
      wavesurfer.value.play()
    }
  })

  wavesurfer.value.on('play', () => {
    isPlaying.value = true
    // 通知其他播放器暂停
    audioPlayBus.emit(playerId.value)
  })

  wavesurfer.value.on('pause', () => {
    isPlaying.value = false
  })

  wavesurfer.value.on('finish', () => {
    isPlaying.value = false
    // 播放结束后将游标重置到开始位置
    if (wavesurfer.value) {
      wavesurfer.value.seekTo(0)
    }
  })

  wavesurfer.value.on('error', (_err: unknown) => {
    loading.value = false
    // WaveSurfer 解码失败时（常见于短小的 Opus OGG 文件），回退到原生 audio 元素
    useFallback.value = true
  })

  // 加载音频
  if (props.audioUrl) {
    loadAudio(props.audioUrl)
  }
}

/**
 * 加载音频
 */
function loadAudio(url: string) {
  if (!url) {
    return
  }
  
  if (!wavesurfer.value) {
    loadError.value = true
    return
  }

  loading.value = true
  loadError.value = false

  try {
    // 检查是否为 Blob URL 或 Data URL
    if (url.startsWith('blob:') || url.startsWith('data:')) {
      // 对于 blob URL，直接加载
      wavesurfer.value.load(url)
    } else {
      // 使用统一的资源 URL 处理函数
      const audioUrl = getResourceUrl(url)
      if (audioUrl) {
        wavesurfer.value.load(audioUrl)
      } else {
        loading.value = false
        loadError.value = true
      }
    }
  } catch (error) {
    loading.value = false
    loadError.value = true
  }
}

/**
 * 获取完整音频 URL
 */
function getFullAudioUrl(url: string): string {
  if (url.startsWith('blob:') || url.startsWith('data:') || url.startsWith('http')) {
    return url
  }
  return getResourceUrl(url) || url
}

/**
 * 格式化时间 mm:ss
 */
function formatTime(seconds: number): string {
  if (!isFinite(seconds) || seconds < 0) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

/**
 * 回退模式：时间更新
 */
function onFallbackTimeUpdate() {
  const audio = fallbackAudioRef.value
  if (!audio || !audio.duration) return
  fallbackProgress.value = (audio.currentTime / audio.duration) * 100
  fallbackCurrentTime.value = formatTime(audio.currentTime)
}

/**
 * 回退模式：元数据加载完成
 */
function onFallbackLoaded() {
  const audio = fallbackAudioRef.value
  if (!audio) return
  fallbackDuration.value = formatTime(audio.duration)
}

/**
 * 回退模式：点击进度条跳转
 */
function onFallbackSeek(e: MouseEvent) {
  const audio = fallbackAudioRef.value
  const bar = e.currentTarget as HTMLElement
  if (!audio || !audio.duration || !bar) return
  const rect = bar.getBoundingClientRect()
  const ratio = (e.clientX - rect.left) / rect.width
  audio.currentTime = ratio * audio.duration
}

/**
 * 回退模式：切换播放/暂停
 */
function toggleFallbackPlay() {
  const audio = fallbackAudioRef.value
  if (!audio) return
  if (audio.paused) {
    audioPlayBus.emit(playerId.value)
    audio.play()
  } else {
    audio.pause()
  }
}

/**
 * 切换播放/暂停
 */
function togglePlay() {
  if (useFallback.value) {
    toggleFallbackPlay()
    return
  }
  if (loading.value || !wavesurfer.value) return
  wavesurfer.value.playPause()
}

// 监听其他播放器的播放事件
audioPlayBus.on((id) => {
  if (id !== playerId.value && isPlaying.value) {
    if (useFallback.value) {
      fallbackAudioRef.value?.pause()
    } else if (wavesurfer.value) {
      wavesurfer.value.pause()
    }
  }
})

// 监听全局停止事件
stopAllAudioBus.on(() => {
  if (isPlaying.value) {
    if (useFallback.value) {
      fallbackAudioRef.value?.pause()
    } else if (wavesurfer.value) {
      wavesurfer.value.pause()
    }
  }
})

// 监听 audioUrl 变化
watch(
  () => props.audioUrl,
  (newUrl) => {
    if (wavesurfer.value && newUrl) {
      loading.value = true
      loadError.value = false
      loadAudio(newUrl)
    } else if (!wavesurfer.value && newUrl) {
      loadError.value = true
    }
  },
)

onMounted(() => {
  // 生成唯一 ID
  playerId.value = `player_${Date.now()}_${Math.floor(Math.random() * 1000)}`
  initWaveSurfer()
})

onBeforeUnmount(() => {
  if (useFallback.value && fallbackAudioRef.value) {
    fallbackAudioRef.value.pause()
  }
  if (wavesurfer.value) {
    if (isPlaying.value) {
      wavesurfer.value.pause()
    }
    wavesurfer.value.destroy()
  }
})
</script>

<template>
  <div v-if="loadError" class="audio-error">
    <span style="color: var(--ant-color-text-tertiary)">音频加载失败</span>
  </div>
  <div v-else-if="useFallback" class="audio-player-container">
    <div class="player-controls">
      <a-button
        type="primary"
        shape="circle"
        size="small"
        @click="togglePlay"
      >
        <template #icon>
          <PauseCircleOutlined v-if="isPlaying" />
          <PlayCircleOutlined v-else />
        </template>
      </a-button>
    </div>
    <audio
      ref="fallbackAudioRef"
      :src="getFullAudioUrl(audioUrl)"
      preload="metadata"
      @play="isPlaying = true"
      @pause="isPlaying = false"
      @ended="isPlaying = false; fallbackProgress = 0"
      @timeupdate="onFallbackTimeUpdate"
      @loadedmetadata="onFallbackLoaded"
      @error="loadError = true"
    />
    <div class="fallback-waveform" @click="onFallbackSeek">
      <div class="fallback-track">
        <div class="fallback-progress" :style="{ width: fallbackProgress + '%' }"></div>
      </div>
    </div>
    <span v-if="fallbackDuration" class="fallback-time">
      {{ fallbackCurrentTime || '0:00' }} / {{ fallbackDuration }}
    </span>
  </div>
  <div v-else class="audio-player-container">
    <div class="player-controls">
      <a-button
        type="primary"
        shape="circle"
        size="small"
        :loading="loading"
        :disabled="loadError"
        @click="togglePlay"
      >
        <template #icon>
          <PauseCircleOutlined v-if="isPlaying" />
          <PlayCircleOutlined v-else />
        </template>
      </a-button>
    </div>
    <div ref="waveformRef" class="waveform-container"></div>
  </div>
</template>

<style scoped lang="scss">
.audio-player-container {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 5px;
  min-height: 50px;
}

.player-controls {
  margin-right: 10px;
}

.waveform-container {
  flex: 1;
  height: 40px;
}

.fallback-waveform {
  flex: 1;
  height: 40px;
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 0 4px;
}

.fallback-track {
  width: 100%;
  height: 6px;
  background: var(--ant-color-border);
  border-radius: 3px;
  overflow: hidden;
  position: relative;
}

.fallback-progress {
  height: 100%;
  background: var(--ant-color-primary);
  border-radius: 3px;
  transition: width 0.1s linear;
}

.fallback-time {
  margin-left: 8px;
  color: var(--ant-color-text-secondary);
  font-size: 12px;
  white-space: nowrap;
  min-width: 70px;
  text-align: right;
}

.audio-error {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 5px;
  min-height: 50px;
}
</style>

