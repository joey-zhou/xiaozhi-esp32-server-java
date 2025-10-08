// 音频处理服务 - Vue3 TypeScript版本

import { log } from './websocket'

// =============================
// 类型定义
// =============================

interface OpusDecoderModule {
  _opus_decoder_get_size: (channels: number) => number
  _opus_decoder_init: (decoder: number, sampleRate: number, channels: number) => number
  _opus_decode: (
    decoder: number,
    data: number,
    len: number,
    pcm: number,
    frameSize: number,
    decodeFec: number
  ) => number
  _malloc: (size: number) => number
  _free: (ptr: number) => void
  HEAPU8: Uint8Array
  HEAP16: Int16Array
}

interface OpusDecoder {
  channels: number
  rate: number
  frameSize: number
  module: OpusDecoderModule
  decoderPtr: number | null
  init: () => boolean
  decode: (opusData: Uint8Array) => Int16Array
  destroy: () => void
}

interface AudioConfig {
  sampleRate: number
  channels: number
  frameSize: number
}

interface StreamingContext {
  queue: number[]
  playing: boolean
  endOfStream: boolean
  source: AudioBufferSourceNode | null
  totalSamples: number
  lastPlayTime: number
  analyser: AnalyserNode | null
  decodeOpusFrames: (opusFrames: Uint8Array[]) => Promise<void>
  startPlaying: () => void
}

declare global {
  interface Window {
    Module?: any
    ModuleInstance?: OpusDecoderModule
    audioContext?: AudioContext
    streamingContext?: StreamingContext
    enableAudio?: () => Promise<boolean>
  }
}

// =============================
// 配置
// =============================

const defaultConfig: AudioConfig = {
  sampleRate: 16000,
  channels: 1,
  frameSize: 960 // 60ms @ 16kHz
}

// =============================
// 状态变量
// =============================

let audioContext: AudioContext | null = null
let opusDecoder: OpusDecoder | null = null
let audioBufferQueue: Uint8Array[] = []
let isAudioBuffering = false
let isAudioPlaying = false
let streamingContext: StreamingContext | null = null
let audioContextResumePromise: Promise<AudioContext> | null = null

// =============================
// 音频上下文初始化
// =============================

async function initAudioContext(): Promise<AudioContext | null> {
  if (audioContext) {
    if (audioContext.state === 'suspended' && !audioContextResumePromise) {
      audioContextResumePromise = new Promise((resolve) => {
        log('音频上下文已暂停。需要用户交互才能恢复。', 'warning')

        const resumeAudioContext = async () => {
          try {
            if (audioContext) {
              await audioContext.resume()
              log('音频上下文已通过用户交互恢复', 'success')
              resolve(audioContext)

              ;['click', 'touchstart', 'keydown'].forEach(event => {
                document.removeEventListener(event, resumeAudioContext)
              })
              audioContextResumePromise = null
            }
          } catch (err) {
            log('恢复音频上下文失败: ' + err, 'error')
          }
        }

        ;['click', 'touchstart', 'keydown'].forEach(event => {
          document.addEventListener(event, resumeAudioContext, { once: false })
        })
      })

      return audioContextResumePromise
    }
    return audioContext
  }

  try {
    const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext
    audioContext = new AudioContextClass({
      sampleRate: defaultConfig.sampleRate,
      latencyHint: 'interactive'
    })

    if (audioContext.state === 'suspended') {
      log('新创建的音频上下文处于暂停状态。需要用户交互才能启动。', 'warning')

      audioContextResumePromise = new Promise((resolve) => {
        const resumeAudioContext = async () => {
          try {
            if (audioContext) {
              await audioContext.resume()
              log('音频上下文已通过用户交互启动', 'success')
              resolve(audioContext)

              ;['click', 'touchstart', 'keydown'].forEach(event => {
                document.removeEventListener(event, resumeAudioContext)
              })
              audioContextResumePromise = null
            }
          } catch (err) {
            log('启动音频上下文失败: ' + err, 'error')
          }
        }

        ;['click', 'touchstart', 'keydown'].forEach(event => {
          document.addEventListener(event, resumeAudioContext, { once: false })
        })
      })

      return audioContextResumePromise
    }

    window.audioContext = audioContext

    return audioContext
  } catch (error) {
    log('初始化音频上下文失败:' + error, 'error')
    return null
  }
}

// =============================
// Opus 库加载
// =============================

function checkOpusLoaded(): boolean {
  try {
    if (typeof window.Module === 'undefined') {
      return false
    }

    if (
      typeof window.Module.instance !== 'undefined' &&
      typeof window.Module.instance._opus_decoder_get_size === 'function'
    ) {
      window.ModuleInstance = window.Module.instance
      log('Opus库加载成功（使用Module.instance）', 'success')
      return true
    }

    if (typeof window.Module._opus_decoder_get_size === 'function') {
      window.ModuleInstance = window.Module
      log('Opus库加载成功（使用全局Module）', 'success')
      return true
    }

    if (
      window.ModuleInstance &&
      typeof window.ModuleInstance._opus_decoder_get_size === 'function'
    ) {
      log('Opus库已加载（使用ModuleInstance）', 'success')
      return true
    }

    return false
  } catch (err) {
    log(`Opus库检查失败: ${err}`, 'error')
    return false
  }
}

export function loadOpusLibrary(): Promise<boolean> {
  return new Promise(resolve => {
    if (checkOpusLoaded()) {
      resolve(true)
      return
    }

    log('尝试加载libopus.js', 'info')

    const possiblePaths = [
      '/libopus.js',
      '/js/libopus.js',
      '/static/js/libopus.js',
      './libopus.js',
      '../js/libopus.js'
    ]

    const script = document.createElement('script')
    script.async = true

    script.onload = () => {
      log('libopus.js脚本加载成功，等待初始化', 'success')

      const maxAttempts = 100
      let attempts = 0

      const checkModule = () => {
        if (checkOpusLoaded()) {
          log('Opus库初始化成功', 'success')
          resolve(true)
          return
        }

        if (attempts >= maxAttempts) {
          log('Opus库初始化超时', 'error')
          resolve(false)
          return
        }

        attempts++
        setTimeout(checkModule, 100)
      }

      checkModule()
    }

    script.onerror = () => {
      log('libopus.js加载失败，尝试下一个路径', 'warning')
      tryNextPath()
    }

    let pathIndex = 0

    function tryNextPath() {
      if (pathIndex >= possiblePaths.length) {
        log('所有路径都尝试失败', 'error')
        resolve(false)
        return
      }

      const path = possiblePaths[pathIndex]
      pathIndex++

      log(`尝试从路径加载: ${path}`, 'info')
      script.src = path
      document.head.appendChild(script)
    }

    tryNextPath()
  })
}

// =============================
// Opus 解码器
// =============================

function createOpusDecoder(mod: OpusDecoderModule): OpusDecoder {
  try {
    const SAMPLE_RATE = 16000
    const CHANNELS = 1
    const FRAME_SIZE = 960

    const decoder: OpusDecoder = {
      channels: CHANNELS,
      rate: SAMPLE_RATE,
      frameSize: FRAME_SIZE,
      module: mod,
      decoderPtr: null,

      init: function () {
        if (this.decoderPtr) return true

        const decoderSize = mod._opus_decoder_get_size(this.channels)
        log('Opus解码器大小:' + decoderSize + '字节', 'debug')

        this.decoderPtr = mod._malloc(decoderSize)
        if (!this.decoderPtr) {
          throw new Error('无法分配解码器内存')
        }

        const err = mod._opus_decoder_init(this.decoderPtr, this.rate, this.channels)

        if (err < 0) {
          this.destroy()
          throw new Error(`Opus解码器初始化失败: ${err}`)
        }

        log('Opus解码器初始化成功', 'success')
        return true
      },

      decode: function (opusData: Uint8Array): Int16Array {
        if (!this.decoderPtr) {
          if (!this.init()) {
            throw new Error('解码器未初始化且无法初始化')
          }
        }

        try {
          const mod = this.module

          const opusPtr = mod._malloc(opusData.length)
          mod.HEAPU8.set(opusData, opusPtr)

          const pcmPtr = mod._malloc(this.frameSize * 2)

          const decodedSamples = mod._opus_decode(
            this.decoderPtr!,
            opusPtr,
            opusData.length,
            pcmPtr,
            this.frameSize,
            0
          )

          if (decodedSamples < 0) {
            mod._free(opusPtr)
            mod._free(pcmPtr)
            throw new Error(`Opus解码失败: ${decodedSamples}`)
          }

          const decodedData = new Int16Array(decodedSamples)
          for (let i = 0; i < decodedSamples; i++) {
            decodedData[i] = mod.HEAP16[(pcmPtr >> 1) + i]
          }

          mod._free(opusPtr)
          mod._free(pcmPtr)

          return decodedData
        } catch (error) {
          log('Opus解码错误:' + error, 'error')
          return new Int16Array(0)
        }
      },

      destroy: function () {
        if (this.decoderPtr) {
          this.module._free(this.decoderPtr)
          this.decoderPtr = null
        }
      }
    }

    if (!decoder.init()) {
      throw new Error('Opus解码器初始化失败')
    }

    opusDecoder = decoder
    return decoder
  } catch (error) {
    log('Opus解码器初始化失败:' + error, 'error')
    opusDecoder = null
    throw error
  }
}

export async function initOpusDecoder(): Promise<OpusDecoder | null> {
  if (opusDecoder) {
    return opusDecoder
  }

  try {
    const opusLoaded = await loadOpusLibrary()
    if (!opusLoaded) {
      throw new Error('Opus库未加载')
    }

    const mod = window.ModuleInstance
    if (!mod) {
      throw new Error('ModuleInstance不可用')
    }

    return createOpusDecoder(mod)
  } catch (error) {
    log(`初始化Opus解码器失败:` + error, 'error')
    throw error
  }
}

// =============================
// 音频播放
// =============================

function convertInt16ToFloat32(int16Data: Int16Array): number[] {
  const float32Data: number[] = []
  for (let i = 0; i < int16Data.length; i++) {
    float32Data.push(int16Data[i] / (int16Data[i] < 0 ? 0x8000 : 0x7fff))
  }
  return float32Data
}

function resetAudioBuffer(): void {
  audioBufferQueue = []
  isAudioBuffering = false
  isAudioPlaying = false
}

function addAudioToBuffer(opusData: Uint8Array): boolean {
  audioBufferQueue.push(opusData)

  if (audioBufferQueue.length === 1 && !isAudioBuffering && !isAudioPlaying) {
    startAudioBuffering()
  }

  return true
}

function startAudioBuffering(): boolean {
  if (isAudioBuffering || isAudioPlaying) return false

  isAudioBuffering = true
  log('开始音频缓冲...', 'info')

  initOpusDecoder().catch(error => {
    log(`预初始化Opus解码器失败: ${error}`, 'warning')
  })

  setTimeout(() => {
    if (isAudioBuffering && audioBufferQueue.length > 0) {
      log(`缓冲超时，当前缓冲包数: ${audioBufferQueue.length}，开始播放`, 'info')
      playBufferedAudio()
    }
  }, 300)

  const bufferThreshold = 3
  const bufferCheckInterval = setInterval(() => {
    if (!isAudioBuffering) {
      clearInterval(bufferCheckInterval)
      return
    }

    if (audioBufferQueue.length >= bufferThreshold) {
      clearInterval(bufferCheckInterval)
      log(`已缓冲 ${audioBufferQueue.length} 个音频包，开始播放`, 'info')
      playBufferedAudio()
    }
  }, 50)

  return true
}

async function playBufferedAudio(): Promise<boolean> {
  if (isAudioPlaying || audioBufferQueue.length === 0) return false

  isAudioPlaying = true
  isAudioBuffering = false

  try {
    if (!audioContext) {
      audioContext = await initAudioContext()
    }

    if (!audioContext || audioContext.state === 'suspended') {
      log('音频上下文被暂停，等待用户交互...', 'warning')
      isAudioPlaying = false
      return false
    }

    if (!opusDecoder) {
      log('初始化Opus解码器...', 'info')
      try {
        opusDecoder = await initOpusDecoder()
        if (!opusDecoder) {
          throw new Error('解码器初始化失败')
        }
        log('Opus解码器初始化成功', 'success')
      } catch (error) {
        log('Opus解码器初始化失败: ' + error, 'error')
        isAudioPlaying = false
        return false
      }
    }

    if (!streamingContext) {
      streamingContext = {
        queue: [],
        playing: false,
        endOfStream: false,
        source: null,
        totalSamples: 0,
        lastPlayTime: 0,
        analyser: null,

        decodeOpusFrames: async function (opusFrames: Uint8Array[]) {
          if (!opusDecoder) {
            log('Opus解码器未初始化，无法解码', 'error')
            return
          }

          let decodedSamples: number[] = []
          for (const frame of opusFrames) {
            try {
              const frameData = opusDecoder.decode(frame)
              if (frameData && frameData.length > 0) {
                const floatData = convertInt16ToFloat32(frameData)
                decodedSamples.push(...floatData)
              }
            } catch (error) {
              log('Opus解码失败: ' + error, 'error')
            }
          }

          if (decodedSamples.length > 0) {
            this.queue.push(...decodedSamples)
            this.totalSamples += decodedSamples.length

            const minSamples = defaultConfig.sampleRate * 0.1
            if (!this.playing && this.queue.length >= minSamples) {
              this.startPlaying()
            }
          } else {
            log('没有成功解码的样本', 'warning')
          }
        },

        startPlaying: function () {
          if (this.playing || this.queue.length === 0 || !audioContext) return

          if (audioContext.state === 'suspended') {
            log('音频上下文仍处于暂停状态，无法播放', 'warning')
            return
          }

          this.playing = true

          const minPlaySamples = Math.min(this.queue.length, defaultConfig.sampleRate)
          const currentSamples = this.queue.splice(0, minPlaySamples)
          const audioBuffer = audioContext.createBuffer(
            defaultConfig.channels,
            currentSamples.length,
            defaultConfig.sampleRate
          )

          const channelData = audioBuffer.getChannelData(0)
          for (let i = 0; i < currentSamples.length; i++) {
            channelData[i] = currentSamples[i]
          }

          this.source = audioContext.createBufferSource()
          this.source.buffer = audioBuffer

          const gainNode = audioContext.createGain()

          const fadeDuration = 0.02
          gainNode.gain.setValueAtTime(0, audioContext.currentTime)
          gainNode.gain.linearRampToValueAtTime(1, audioContext.currentTime + fadeDuration)

          const duration = audioBuffer.duration
          if (duration > fadeDuration * 2) {
            gainNode.gain.setValueAtTime(1, audioContext.currentTime + duration - fadeDuration)
            gainNode.gain.linearRampToValueAtTime(0, audioContext.currentTime + duration)
          }

          const analyserNode = audioContext.createAnalyser()
          analyserNode.fftSize = 256
          analyserNode.smoothingTimeConstant = 0.8

          this.source.connect(analyserNode)
          analyserNode.connect(gainNode)
          gainNode.connect(audioContext.destination)

          this.analyser = analyserNode

          this.lastPlayTime = audioContext.currentTime

          log(
            `开始播放 ${currentSamples.length} 个样本，约 ${(currentSamples.length / defaultConfig.sampleRate).toFixed(2)} 秒`,
            'debug'
          )

          this.source.onended = () => {
            this.source = null
            this.analyser = null
            this.playing = false

            if (this.queue.length > 0) {
              setTimeout(() => this.startPlaying(), 10)
            } else if (audioBufferQueue.length > 0) {
              const frames = [...audioBufferQueue]
              audioBufferQueue = []
              this.decodeOpusFrames(frames)
            } else if (this.endOfStream) {
              log('音频播放完成', 'info')
              isAudioPlaying = false
              streamingContext = null
              window.streamingContext = undefined
            } else {
              setTimeout(() => {
                if (this.queue.length === 0 && audioBufferQueue.length > 0) {
                  const frames = [...audioBufferQueue]
                  audioBufferQueue = []
                  this.decodeOpusFrames(frames)
                } else if (this.queue.length === 0 && audioBufferQueue.length === 0) {
                  log('音频播放完成 (超时)', 'info')
                  isAudioPlaying = false
                  streamingContext = null
                  window.streamingContext = undefined
                }
              }, 500)
            }
          }

          this.source.start()
        }
      }

      window.streamingContext = streamingContext
    }

    const frames = [...audioBufferQueue]
    audioBufferQueue = []

    await streamingContext.decodeOpusFrames(frames)
    return true
  } catch (error) {
    log(`播放已缓冲的音频出错:` + error, 'error')
    isAudioPlaying = false
    streamingContext = null
    window.streamingContext = undefined

    return false
  }
}

export function stopAudioPlayback(): boolean {
  try {
    isAudioPlaying = false
    isAudioBuffering = false

    if (streamingContext && streamingContext.source) {
      try {
        streamingContext.source.stop()
        streamingContext.source = null
        streamingContext.analyser = null
      } catch (e) {
        // 忽略已停止的音频源错误
      }
    }

    audioBufferQueue = []
    streamingContext = null

    window.streamingContext = undefined

    if (window.dispatchEvent) {
      window.dispatchEvent(new CustomEvent('audio-playback-stopped'))
    }

    log('音频播放已停止', 'info')
    return true
  } catch (error) {
    log(`停止音频播放失败:` + error, 'error')
    return false
  }
}

// =============================
// 导出函数
// =============================

export async function initAudio(): Promise<boolean> {
  try {
    const context = await initAudioContext()

    window.enableAudio = async function () {
      try {
        if (audioContext && audioContext.state === 'suspended') {
          await audioContext.resume()
          log('音频上下文已恢复', 'success')
        }

        let opusLoaded = false
        for (let i = 0; i < 3; i++) {
          try {
            opusLoaded = await loadOpusLibrary()
            if (opusLoaded) {
              log(`Opus库加载成功 (尝试 ${i + 1}/3)`, 'success')
              break
            }
          } catch (err) {
            log(`尝试 ${i + 1}/3 加载libopus.js失败，将重试`, 'warning')
          }
        }

        if (!opusLoaded) {
          log('所有Opus库加载尝试均失败，音频播放功能将不可用', 'error')
          return false
        }

        try {
          await initOpusDecoder()
          log('Opus解码器初始化成功', 'success')
          return true
        } catch (err) {
          log(`Opus解码器初始化失败: ${err}，音频播放功能将不可用`, 'error')
          return false
        }
      } catch (error) {
        log('启用音频失败:' + error, 'error')
        return false
      }
    }

    await loadOpusLibrary()

    log('音频系统已初始化。请通过用户交互启用音频功能。', 'info')

    return true
  } catch (error) {
    log('初始化音频失败:' + error, 'error')
    return false
  }
}

export async function handleBinaryAudioMessage(data: ArrayBuffer): Promise<boolean> {
  try {
    log(`收到ArrayBuffer音频数据，大小: ${data.byteLength}字节`, 'debug')

    const opusData = new Uint8Array(data)

    if (opusData.length > 0) {
      addAudioToBuffer(opusData)

      if (window.dispatchEvent) {
        window.dispatchEvent(
          new CustomEvent('audio-data-received', {
            detail: { dataLength: opusData.length }
          })
        )
      }

      return true
    } else {
      log('收到空音频数据帧，可能是结束标志', 'warning')

      if (audioBufferQueue.length > 0 && !isAudioPlaying) {
        playBufferedAudio()
      }

      if (isAudioPlaying && streamingContext) {
        streamingContext.endOfStream = true
      }

      if (window.dispatchEvent) {
        window.dispatchEvent(new CustomEvent('audio-playback-ended'))
      }

      return true
    }
  } catch (error) {
    log('处理二进制消息出错:' + error, 'error')
    return false
  }
}

export function cleanupAudio(): boolean {
  try {
    stopAudioPlayback()

    if (opusDecoder && opusDecoder.destroy) {
      opusDecoder.destroy()
      opusDecoder = null
    }

    if (audioContext && audioContext.state !== 'closed') {
      audioContext.close()
      audioContext = null
    }

    resetAudioBuffer()

    log('音频资源已清理', 'info')
    return true
  } catch (error) {
    log('清理音频资源失败:' + error, 'error')
    return false
  }
}

export function getAudioState() {
  return {
    isAudioBuffering,
    isAudioPlaying,
    audioBufferQueue,
    analyser: streamingContext && streamingContext.analyser
  }
}

