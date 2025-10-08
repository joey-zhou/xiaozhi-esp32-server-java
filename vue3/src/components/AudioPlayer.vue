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

const emit = defineEmits<{
  audioLoadError: []
}>()

// 状态
const wavesurfer = ref<WaveSurfer | null>(null)
const isPlaying = ref(false)
const loading = ref(true)
const loadError = ref(false)
const playerId = ref('')
const waveformRef = ref<HTMLDivElement>()

// 使用 VueUse 的事件总线
const audioPlayBus = useEventBus<string>('audio-play')
const stopAllAudioBus = useEventBus<void>('stop-all-audio')

/**
 * 初始化 WaveSurfer
 */
function initWaveSurfer() {
  if (!waveformRef.value) return

  // 创建 wavesurfer 实例
  wavesurfer.value = WaveSurfer.create({
    container: waveformRef.value,
    waveColor: '#ddd',
    progressColor: '#1890ff',
    cursorColor: 'transparent',
    barWidth: 2,
    barRadius: 2,
    barGap: 1,
    height: 40,
    normalize: true,
  })

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

  wavesurfer.value.on('error', (error) => {
    console.debug('音频加载失败:', props.audioUrl, error)
    loading.value = false
    loadError.value = true
    emit('audioLoadError')
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
  if (!url || !wavesurfer.value) return

  // 检查是否为 Blob URL 或 Data URL
  if (url.startsWith('blob:') || url.startsWith('data:')) {
    wavesurfer.value.load(url)
  } else {
    // 使用统一的资源 URL 处理函数
    const audioUrl = getResourceUrl(url)
    if (audioUrl) {
      wavesurfer.value.load(audioUrl)
    }
  }
}

/**
 * 切换播放/暂停
 */
function togglePlay() {
  if (loading.value || !wavesurfer.value) return
  wavesurfer.value.playPause()
}

// 监听其他播放器的播放事件
audioPlayBus.on((id) => {
  if (id !== playerId.value && isPlaying.value && wavesurfer.value) {
    wavesurfer.value.pause()
  }
})

// 监听全局停止事件
stopAllAudioBus.on(() => {
  if (isPlaying.value && wavesurfer.value) {
    wavesurfer.value.pause()
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
    }
  },
)

onMounted(() => {
  // 生成唯一 ID
  playerId.value = `player_${Date.now()}_${Math.floor(Math.random() * 1000)}`
  initWaveSurfer()
})

onBeforeUnmount(() => {
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
    <span style="color: #999">音频加载失败</span>
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

.audio-error {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 5px;
  min-height: 50px;
}
</style>

