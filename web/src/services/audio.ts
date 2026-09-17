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
  // 解码后的 PCM 按帧分片存放：number[] 每个样本都装箱，且 splice 取头部要整体搬移
  queue: Float32Array[]
  queuedSamples: number
  playing: boolean
  endOfStream: boolean
  source: AudioBufferSourceNode | null
  totalSamples: number
  lastPlayTime: number
  analyser: AnalyserNode | null
  decodeOpusFrames: (opusFrames: Uint8Array[]) => Promise<void>
  startPlaying: () => void
}

// Opus Module 可能的结构
interface OpusModule {
  instance?: OpusDecoderModule
  _opus_decoder_get_size?: (channels: number) => number
  _opus_decoder_init?: (decoder: number, sampleRate: number, channels: number) => number
  _opus_decode?: (
    decoder: number,
    data: number,
    len: number,
    pcm: number,
    frameSize: number,
    decodeFec: number
  ) => number
  _malloc?: (size: number) => number
  _free?: (ptr: number) => void
  HEAPU8?: Uint8Array
  HEAP16?: Int16Array
}

declare global {
  interface Window {
    Module?: OpusModule
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
const MAX_BUFFER_QUEUE_LEN = 50 // 约束缓冲帧数上限，防止长时间未起播时内存无界增长
let isAudioBuffering = false
let isAudioPlaying = false
let streamingContext: StreamingContext | null = null
let audioContextResumePromise: Promise<AudioContext | null> | null = null
// tts/stop 早于解码链路建立时的结束标记，等 streamingContext 建好再落上去
let pendingStreamEnd = false

// =============================
// 音频上下文初始化
// =============================

const RESUME_GESTURES = ['click', 'touchstart', 'keydown'] as const

/**
 * 挂上用户交互监听，等一次交互后 resume 音频上下文。
 * 无论 resume 成功还是抛错都必须摘掉监听并 settle，否则失败一次就永远挂着监听、调用方也永远等不到结果。
 */
function resumeOnUserGesture(failureLog: string): Promise<AudioContext | null> {
  return new Promise((resolve) => {
    const detach = () => {
      RESUME_GESTURES.forEach(event => document.removeEventListener(event, onGesture))
      audioContextResumePromise = null
    }

    const onGesture = async () => {
      if (!audioContext) {
        detach()
        resolve(null)
        return
      }
      try {
        await audioContext.resume()
        log('音频上下文已通过用户交互恢复', 'success')
        detach()
        resolve(audioContext)
      } catch (err) {
        log(`${failureLog}: ${err}`, 'error')
        detach()
        resolve(null)
      }
    }

    RESUME_GESTURES.forEach(event => document.addEventListener(event, onGesture))
  })
}

async function initAudioContext(): Promise<AudioContext | null> {
  if (audioContext) {
    if (audioContext.state === 'suspended' && !audioContextResumePromise) {
      log('音频上下文已暂停。需要用户交互才能恢复。', 'warning')
      audioContextResumePromise = resumeOnUserGesture('恢复音频上下文失败')
      return audioContextResumePromise
    }
    return audioContext
  }

  try {
    const AudioContextClass = window.AudioContext || (window as unknown as Record<string, unknown>).webkitAudioContext
    audioContext = new AudioContextClass({
      sampleRate: defaultConfig.sampleRate,
      latencyHint: 'interactive'
    })
    window.audioContext = audioContext

    if (audioContext.state === 'suspended') {
      log('新创建的音频上下文处于暂停状态。需要用户交互才能启动。', 'warning')
      audioContextResumePromise = resumeOnUserGesture('启动音频上下文失败')
      return audioContextResumePromise
    }

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
    if (!window.Module) {
      return false
    }

    const module = window.Module

    // 检查 Module.instance 是否存在且有效
    if (
      module.instance &&
      typeof module.instance._opus_decoder_get_size === 'function'
    ) {
      window.ModuleInstance = module.instance
      log('Opus库加载成功（使用Module.instance）', 'success')
      return true
    }

    // 检查 Module 本身是否包含解码器方法
    if (typeof module._opus_decoder_get_size === 'function') {
      // 确保 module 符合 OpusDecoderModule 接口
      const decoderModule: OpusDecoderModule = {
        _opus_decoder_get_size: module._opus_decoder_get_size,
        _opus_decoder_init: module._opus_decoder_init!,
        _opus_decode: module._opus_decode!,
        _malloc: module._malloc!,
        _free: module._free!,
        HEAPU8: module.HEAPU8!,
        HEAP16: module.HEAP16!
      }
      window.ModuleInstance = decoderModule
      log('Opus库加载成功（使用全局Module）', 'success')
      return true
    }

    // 检查是否已经设置了 ModuleInstance
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

/** 候选路径：libopus.js 放在 public 根目录，其余是历史部署形态的兜底 */
const OPUS_SCRIPT_PATHS = ['/libopus.js', '/js/libopus.js', '/static/js/libopus.js']

export function loadOpusLibrary(): Promise<boolean> {
  return new Promise(resolve => {
    if (checkOpusLoaded()) {
      resolve(true)
      return
    }

    log('尝试加载libopus.js', 'info')

    // 每个候选路径都要新建 script 元素：已经开始过加载的 script 改 src 不会重新请求，
    // 复用同一个元素会让第二个之后的路径全部形同虚设
    const tryPath = (pathIndex: number) => {
      const path = OPUS_SCRIPT_PATHS[pathIndex]
      if (!path) {
        log('所有路径都尝试失败', 'error')
        resolve(false)
        return
      }

      log(`尝试从路径加载: ${path}`, 'info')
      const script = document.createElement('script')
      script.async = true
      script.src = path

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
        log(`libopus.js 从 ${path} 加载失败，尝试下一个路径`, 'warning')
        script.remove()
        tryPath(pathIndex + 1)
      }

      document.head.appendChild(script)
    }

    tryPath(0)
  })
}

// =============================
// Opus 编码器模块
// =============================

export interface OpusEncoderModule {
  _opus_encoder_get_size: (channels: number) => number
  _opus_encoder_init: (
    encoder: number,
    sampleRate: number,
    channels: number,
    application: number
  ) => number
  _opus_encode: (
    encoder: number,
    pcm: number,
    frameSize: number,
    data: number,
    maxDataBytes: number
  ) => number
  _malloc: (size: number) => number
  _free: (ptr: number) => void
  HEAPU8: Uint8Array
  HEAP16: Int16Array
}

/**
 * 取出已加载的 Opus wasm 模块用于编码。调用前需先 await loadOpusLibrary()。
 * 解码路径缓存的 ModuleInstance 可能是只含解码函数的裁剪对象，因此这里逐个候选检查编码符号。
 */
export function getOpusEncoderModule(): OpusEncoderModule | null {
  const candidates = [window.Module?.instance, window.Module, window.ModuleInstance]

  for (const candidate of candidates) {
    const mod = candidate as unknown as OpusEncoderModule | undefined
    if (
      mod &&
      typeof mod._opus_encode === 'function' &&
      typeof mod._opus_encoder_init === 'function' &&
      typeof mod._opus_encoder_get_size === 'function'
    ) {
      return mod
    }
  }

  return null
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
            const heapValue = mod.HEAP16[(pcmPtr >> 1) + i]
            if (heapValue !== undefined) {
              decodedData[i] = heapValue
            }
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

function convertInt16ToFloat32(int16Data: Int16Array): Float32Array {
  const float32Data = new Float32Array(int16Data.length)
  for (let i = 0; i < int16Data.length; i++) {
    const sample = int16Data[i] ?? 0
    float32Data[i] = sample / (sample < 0 ? 0x8000 : 0x7fff)
  }
  return float32Data
}

/**
 * 从分片队列头部取出至多 maxSamples 个样本拼成一段。
 * 只在跨界的那一片上做切分，不整体搬移队列。
 */
function takeSamples(context: StreamingContext, maxSamples: number): Float32Array {
  const wanted = Math.min(maxSamples, context.queuedSamples)
  const out = new Float32Array(wanted)
  let filled = 0
  while (filled < wanted) {
    const chunk = context.queue[0]
    if (!chunk) break
    const remain = wanted - filled
    if (chunk.length <= remain) {
      out.set(chunk, filled)
      filled += chunk.length
      context.queue.shift()
    } else {
      out.set(chunk.subarray(0, remain), filled)
      context.queue[0] = chunk.subarray(remain)
      filled += remain
    }
  }
  context.queuedSamples -= filled
  return filled === wanted ? out : out.subarray(0, filled)
}

function resetAudioBuffer(): void {
  audioBufferQueue = []
  isAudioBuffering = false
  isAudioPlaying = false
  pendingStreamEnd = false
}

/**
 * 标记本轮 TTS 音频流已结束。
 * 服务端只用 {"type":"tts","state":"stop"} 表达流结束，不会发空音频帧。
 */
export function markStreamEnd(): boolean {
  // 还没起播：streamingContext 要等解码链路建好才有，先记下结束标记
  if (!isAudioPlaying && audioBufferQueue.length > 0) {
    pendingStreamEnd = true
    void playBufferedAudio()
    return true
  }

  if (streamingContext) {
    streamingContext.endOfStream = true
    // 已经播完在等后续数据，不会再有 onended 收尾，这里直接释放
    if (!streamingContext.playing && streamingContext.queuedSamples === 0 && audioBufferQueue.length === 0) {
      isAudioPlaying = false
      streamingContext = null
      window.streamingContext = undefined
    }
    return true
  }

  // 既没在播也没有待播数据：复位，避免 isAudioPlaying 悬挂为 true
  isAudioPlaying = false
  isAudioBuffering = false
  return false
}

function addAudioToBuffer(opusData: Uint8Array): boolean {
  audioBufferQueue.push(opusData)
  if (audioBufferQueue.length > MAX_BUFFER_QUEUE_LEN) {
    audioBufferQueue.shift()
    log('音频缓冲队列超限，丢弃最旧帧', 'warning')
  }

  // 如果没有在播放，启动缓冲流程
  if (!isAudioPlaying && !isAudioBuffering) {
    startAudioBuffering()
  }
  // 如果正在播放但当前没有播放片段，且有足够数据，触发解码
  else if (isAudioPlaying && streamingContext && !streamingContext.playing && audioBufferQueue.length >= 3) {
    log('🔄 播放中收到新数据，立即解码', 'debug')
    const frames = [...audioBufferQueue]
    audioBufferQueue = []
    streamingContext.decodeOpusFrames(frames)
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
        queuedSamples: 0,
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

          let decodedSamples = 0
          for (const frame of opusFrames) {
            try {
              const frameData = opusDecoder.decode(frame)
              if (frameData && frameData.length > 0) {
                this.queue.push(convertInt16ToFloat32(frameData))
                decodedSamples += frameData.length
              }
            } catch (error) {
              log('Opus解码失败: ' + error, 'error')
            }
          }

          if (decodedSamples > 0) {
            this.queuedSamples += decodedSamples
            this.totalSamples += decodedSamples

            const minSamples = defaultConfig.sampleRate * 0.1
            if (!this.playing && this.queuedSamples >= minSamples) {
              this.startPlaying()
            }
          } else {
            log('没有成功解码的样本', 'warning')
          }
        },

        startPlaying: function () {
          if (this.playing || this.queuedSamples === 0 || !audioContext) return

          if (audioContext.state === 'suspended') {
            log('音频上下文仍处于暂停状态，无法播放', 'warning')
            return
          }

          this.playing = true

          const currentSamples = takeSamples(this, defaultConfig.sampleRate)
          const audioBuffer = audioContext.createBuffer(
            defaultConfig.channels,
            currentSamples.length,
            defaultConfig.sampleRate
          )
          audioBuffer.getChannelData(0).set(currentSamples)

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
            // 播完必须逐个 disconnect：只置 null 的话这三个节点还挂在音频图上，不会被回收
            try {
              this.source?.disconnect()
              analyserNode.disconnect()
              gainNode.disconnect()
            } catch {
              // 忽略重复断开
            }
            this.source = null
            this.analyser = null
            this.playing = false

            // 继续播放队列中的数据
            if (this.queuedSamples > 0) {
              setTimeout(() => this.startPlaying(), 10)
            }
            // 检查是否有新的缓冲数据
            else if (audioBufferQueue.length > 0) {
              const frames = [...audioBufferQueue]
              audioBufferQueue = []
              this.decodeOpusFrames(frames)
            }
            // 流已明确结束
            else if (this.endOfStream) {
              log('🏁 音频播放完成（流结束）', 'info')
              isAudioPlaying = false
              streamingContext = null
              window.streamingContext = undefined
            }
            // 等待更多数据（不设置超时，持续等待）
            else {
              log('⏳ 等待更多音频数据...', 'debug')
              // 不做任何处理，保持 isAudioPlaying = true
              // 当新数据到达时，会通过 addAudioToBuffer 触发继续播放
            }
          }

          this.source.start()
        }
      }

      window.streamingContext = streamingContext
    }

    if (pendingStreamEnd) {
      streamingContext.endOfStream = true
      pendingStreamEnd = false
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
    pendingStreamEnd = false

    if (streamingContext && streamingContext.source) {
      try {
        // stop() 也会触发 onended，必须先摘掉回调、清空队列，否则旧 context 会在 10ms 后自己重启播放，
        // 或者把新一轮 TTS 刚塞进 audioBufferQueue 的帧抢去用旧 context 解码
        streamingContext.source.onended = null
        streamingContext.queue = []
        streamingContext.queuedSamples = 0
        streamingContext.source.stop()
        streamingContext.source.disconnect()
        streamingContext.analyser?.disconnect()
      } catch {
        // 忽略已停止的音频源错误
      }
      streamingContext.source = null
      streamingContext.analyser = null
    }

    audioBufferQueue = []
    streamingContext = null

    window.streamingContext = undefined

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
    await initAudioContext()

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
          } catch {
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
    if (opusData.length === 0) {
      return false
    }

    addAudioToBuffer(opusData)
    return true
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

