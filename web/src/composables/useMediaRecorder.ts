import { ref, onBeforeUnmount, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { encodeWAV } from '@/utils/audio'

/** 录音参数 */
export interface RecordingConfig {
  sampleRate: number
  channels: number
  bitDepth: number
  maxDuration: number // 最大录音时长（秒）
}

export interface UseMediaRecorderOptions {
  /** 录音参数，固定不变，不需要响应式 */
  config: RecordingConfig
  /** 录音正常结束（含到时自动停止）后回调，拿到编码好的 WAV 文件 */
  onRecorded: (file: File) => void
}

export interface UseMediaRecorderReturn {
  isRecording: Ref<boolean>
  recordingTime: Ref<number>
  recordError: Ref<string>
  startRecording: () => void
  stopRecording: () => void
}

/**
 * 浏览器录音管线：申请麦克风权限、用 AudioWorklet 采集 PCM 样本、MediaRecorder 兜底，
 * 统一处理计时封顶、轨道释放与权限报错。供"录一段音频转成上传文件"的场景复用。
 */
export function useMediaRecorder(options: UseMediaRecorderOptions): UseMediaRecorderReturn {
  const { t } = useI18n()
  const { config, onRecorded } = options

  const isRecording = ref(false)
  const recordingTime = ref(0)
  const recordError = ref('')
  const recordTimer = ref<number | null>(null)

  const mediaRecorder = ref<MediaRecorder | null>(null)
  const mediaStream = ref<MediaStream | null>(null)
  const audioChunks = ref<Blob[]>([])
  const audioContext = ref<AudioContext | null>(null)
  const audioInput = ref<MediaStreamAudioSourceNode | null>(null)
  const workletNode = ref<AudioWorkletNode | null>(null)
  const analyser = ref<AnalyserNode | null>(null)
  const audioBuffers = ref<Float32Array[]>([])
  const actualSampleRate = ref(0)

  /** 释放麦克风轨道与音频图节点，不做任何数据处理 */
  const releaseResources = () => {
    if (mediaRecorder.value) {
      if (mediaRecorder.value.state !== 'inactive') {
        mediaRecorder.value.stop()
      }
      mediaRecorder.value = null
    }

    // MediaRecorder.stop() 与 AudioContext.close() 都不停轨道，
    // 必须显式 stop，否则麦克风被本页一直占用到刷新
    mediaStream.value?.getTracks().forEach(track => track.stop())
    mediaStream.value = null

    if (workletNode.value) {
      workletNode.value.disconnect()
      workletNode.value.port.onmessage = null
      audioInput.value?.disconnect()
      analyser.value?.disconnect()
      workletNode.value = null
      audioInput.value = null
      analyser.value = null
    }

    if (audioContext.value) {
      if (audioContext.value.state !== 'closed' && audioContext.value.close) {
        audioContext.value.close()
      }
      audioContext.value = null
    }

    if (recordTimer.value) {
      clearInterval(recordTimer.value)
      recordTimer.value = null
    }
  }

  /** 把 AudioWorklet 采集到的样本合并编码为 WAV；采不到样本时回退到 MediaRecorder 的分片 */
  const finalizeRecording = () => {
    try {
      if (audioBuffers.value.length > 0) {
        let totalLength = 0
        for (const buffer of audioBuffers.value) {
          totalLength += buffer.length
        }

        const mergedBuffer = new Float32Array(totalLength)
        let offset = 0
        for (const buffer of audioBuffers.value) {
          mergedBuffer.set(buffer, offset)
          offset += buffer.length
        }

        const wavBlob = encodeWAV(mergedBuffer, actualSampleRate.value || config.sampleRate, config.channels)
        onRecorded(new File([wavBlob], 'recording.wav', { type: 'audio/wav' }))
      } else if (audioChunks.value.length > 0) {
        const audioBlob = new Blob(audioChunks.value, { type: 'audio/wav' })
        onRecorded(new File([audioBlob], 'recording.wav', { type: 'audio/wav' }))
      } else {
        recordError.value = t('common.recordingFailed')
      }
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : t('common.audioProcessFailed')
      recordError.value = errorMessage

      // 主路径失败时退回 MediaRecorder 分片；兜底也失败就只保留主路径的错误提示，
      // 不拿兜底失败的细节覆盖用户已经看到的原因，细节打到控制台供排查
      if (audioChunks.value.length > 0) {
        try {
          const audioBlob = new Blob(audioChunks.value, { type: 'audio/wav' })
          onRecorded(new File([audioBlob], 'recording.wav', { type: 'audio/wav' }))
        } catch (fallbackError: unknown) {
          console.error('录音兜底编码失败:', fallbackError)
        }
      }
    }
  }

  /**
   * 开始录音
   */
  const startRecording = () => {
    // 清除之前的录音和错误信息
    audioChunks.value = []
    audioBuffers.value = []
    recordingTime.value = 0
    recordError.value = ''

    // 检查是否支持 getUserMedia
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      recordError.value = t('common.browserNotSupported')
      return
    }

    // 检查是否支持 AudioContext
    const AudioCtx = window.AudioContext || (window as unknown as Record<string, unknown>).webkitAudioContext
    if (!AudioCtx) {
      recordError.value = t('common.audioContextNotSupported')
      return
    }

    // 定义音频约束
    const constraints = {
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
        sampleRate: config.sampleRate
      }
    }

    // 请求麦克风权限
    navigator.mediaDevices.getUserMedia(constraints)
      .then(async stream => {
        mediaStream.value = stream
        try {
          isRecording.value = true

          // 创建音频上下文
          audioContext.value = new AudioCtx({ sampleRate: config.sampleRate })
          // 浏览器可能不遵循请求的采样率，以实际采样率为准，避免播放变速
          actualSampleRate.value = audioContext.value.sampleRate

          // 创建媒体流源节点
          audioInput.value = audioContext.value.createMediaStreamSource(stream)

          // 创建分析器节点
          analyser.value = audioContext.value.createAnalyser()
          analyser.value.fftSize = 2048

          try {
            // 加载 AudioWorklet 处理器
            await audioContext.value.audioWorklet.addModule('/audio-recorder-processor.js')

            // 创建 AudioWorkletNode
            workletNode.value = new AudioWorkletNode(audioContext.value, 'audio-recorder-processor', {
              numberOfInputs: 1,
              numberOfOutputs: 1,
              channelCount: config.channels
            })

            // 监听来自 AudioWorklet 的消息
            workletNode.value.port.onmessage = (event) => {
              if (event.data.type === 'audio-data') {
                const audioData = new Float32Array(event.data.data)
                audioBuffers.value.push(audioData)
              }
            }

            // 连接节点
            audioInput.value.connect(analyser.value)
            analyser.value.connect(workletNode.value)
            workletNode.value.connect(audioContext.value.destination)
          } catch (workletError) {
            // 如果 AudioWorklet 不支持，显示错误信息
            console.error('AudioWorklet 加载失败:', workletError)
            recordError.value = t('common.audioWorkletNotSupported')
            isRecording.value = false
            stopRecording()
            return
          }

          // 创建媒体记录器
          mediaRecorder.value = new MediaRecorder(stream)
          mediaRecorder.value.ondataavailable = (event) => {
            if (event.data && event.data.size > 0) {
              audioChunks.value.push(event.data)
            }
          }
          mediaRecorder.value.start(1000)

          // 计时器
          recordTimer.value = window.setInterval(() => {
            recordingTime.value++
            if (recordingTime.value >= config.maxDuration) {
              stopRecording()
            }
          }, 1000)
        } catch (error: unknown) {
          const errorMessage = error instanceof Error ? error.message : t('common.unknownError')
          recordError.value = t('common.initRecordingFailed', { reason: errorMessage })
          isRecording.value = false
          stopRecording()
        }
      })
      .catch(error => {
        if (error.name === 'NotAllowedError' || error.name === 'PermissionDeniedError') {
          recordError.value = t('common.microphoneAccessDenied')
        } else if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') {
          recordError.value = t('common.microphoneNotFound')
        } else if (error.name === 'NotReadableError' || error.name === 'TrackStartError') {
          recordError.value = t('common.microphoneOccupied')
        } else if (error.name === 'SecurityError') {
          recordError.value = t('common.securityError')
        } else {
          recordError.value = t('common.microphoneAccessFailed', { reason: `${error.name} - ${error.message}` })
        }
      })
  }

  /**
   * 停止录音
   */
  const stopRecording = () => {
    const wasRecording = isRecording.value
    isRecording.value = false

    releaseResources()

    if (wasRecording) {
      finalizeRecording()
    }
  }

  // 卸载时兜底释放麦克风，避免调用方忘记在自己的 onBeforeUnmount 里收尾
  onBeforeUnmount(() => {
    releaseResources()
  })

  return {
    isRecording,
    recordingTime,
    recordError,
    startRecording,
    stopRecording
  }
}
