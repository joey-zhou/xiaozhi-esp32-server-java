// 麦克风采集服务 - 采集 PCM 并编码为 Opus 帧，帧格式与设备端上行一致

import { loadOpusLibrary, getOpusEncoderModule, type OpusEncoderModule } from './audio'
import { log } from './websocket'

// 与服务端 AudioParams.Opus 保持一致：16kHz 单声道，60ms 一帧
const SAMPLE_RATE = 16000
const CHANNELS = 1
const FRAME_SIZE = 960
const MAX_PACKET_BYTES = 4000
const OPUS_APPLICATION_VOIP = 2048

interface OpusEncoder {
  encode: (pcm: Int16Array) => Uint8Array | null
  destroy: () => void
}

// =============================
// Opus 编码器
// =============================

function createOpusEncoder(mod: OpusEncoderModule): OpusEncoder {
  const encoderSize = mod._opus_encoder_get_size(CHANNELS)
  let encoderPtr: number | null = mod._malloc(encoderSize)

  if (!encoderPtr) {
    throw new Error('无法分配 Opus 编码器内存')
  }

  const err = mod._opus_encoder_init(encoderPtr, SAMPLE_RATE, CHANNELS, OPUS_APPLICATION_VOIP)
  if (err < 0) {
    mod._free(encoderPtr)
    encoderPtr = null
    throw new Error(`Opus 编码器初始化失败: ${err}`)
  }

  return {
    encode(pcm: Int16Array): Uint8Array | null {
      if (encoderPtr === null) {
        return null
      }

      const pcmPtr = mod._malloc(pcm.length * 2)
      const outPtr = mod._malloc(MAX_PACKET_BYTES)

      try {
        mod.HEAP16.set(pcm, pcmPtr >> 1)
        const encodedLength = mod._opus_encode(encoderPtr, pcmPtr, pcm.length, outPtr, MAX_PACKET_BYTES)

        if (encodedLength < 0) {
          log(`Opus 编码失败: ${encodedLength}`, 'error')
          return null
        }
        if (encodedLength === 0) {
          return null
        }

        // 从 wasm 堆复制出来，避免后续 _free 或堆增长导致数据失效
        return new Uint8Array(mod.HEAPU8.subarray(outPtr, outPtr + encodedLength))
      } finally {
        mod._free(pcmPtr)
        mod._free(outPtr)
      }
    },

    destroy() {
      if (encoderPtr !== null) {
        mod._free(encoderPtr)
        encoderPtr = null
      }
    }
  }
}

// =============================
// 状态变量
// =============================

let audioContext: AudioContext | null = null
let mediaStream: MediaStream | null = null
let sourceNode: MediaStreamAudioSourceNode | null = null
let workletNode: AudioWorkletNode | null = null
let encoder: OpusEncoder | null = null
let frameHandler: ((frame: Uint8Array) => void) | null = null
let pendingSamples = new Float32Array(0)
let capturing = false

// =============================
// 采样处理
// =============================

/**
 * 线性插值重采样。仅在浏览器不接受 16kHz 上下文时兜底，
 * 分块边界的插值误差对语音识别可忽略。
 */
function resampleTo16k(input: Float32Array, inputRate: number): Float32Array {
  if (inputRate === SAMPLE_RATE) {
    return input
  }

  const ratio = inputRate / SAMPLE_RATE
  const outputLength = Math.floor(input.length / ratio)
  const output = new Float32Array(outputLength)

  for (let i = 0; i < outputLength; i++) {
    const position = i * ratio
    const index = Math.floor(position)
    const current = input[index] ?? 0
    const next = input[index + 1] ?? current
    output[i] = current + (next - current) * (position - index)
  }

  return output
}

/** 累积采样，凑满一帧就编码并交给回调 */
function pushSamples(samples: Float32Array): void {
  const merged = new Float32Array(pendingSamples.length + samples.length)
  merged.set(pendingSamples)
  merged.set(samples, pendingSamples.length)

  let offset = 0
  while (merged.length - offset >= FRAME_SIZE) {
    const pcm = new Int16Array(FRAME_SIZE)
    for (let i = 0; i < FRAME_SIZE; i++) {
      const sample = Math.max(-1, Math.min(1, merged[offset + i] ?? 0))
      pcm[i] = sample < 0 ? sample * 0x8000 : sample * 0x7fff
    }

    const frame = encoder?.encode(pcm)
    if (frame && frameHandler) {
      frameHandler(frame)
    }
    offset += FRAME_SIZE
  }

  pendingSamples = merged.slice(offset)
}

// =============================
// 对外接口
// =============================

export function isCapturing(): boolean {
  return capturing
}

/**
 * 打开麦克风并持续输出 Opus 帧。
 * 调用方需保证在收到第一帧前服务端已进入聆听状态。
 */
export async function startMicrophoneCapture(handler: (frame: Uint8Array) => void): Promise<void> {
  if (capturing) {
    return
  }

  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    throw new Error('当前浏览器不支持麦克风采集')
  }

  const opusLoaded = await loadOpusLibrary()
  if (!opusLoaded) {
    throw new Error('Opus 库未加载，无法编码上行音频')
  }

  const mod = getOpusEncoderModule()
  if (!mod) {
    throw new Error('当前 Opus 库不包含编码器')
  }

  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: CHANNELS,
        sampleRate: SAMPLE_RATE,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true
      }
    })

    try {
      audioContext = new AudioContext({ sampleRate: SAMPLE_RATE })
    } catch {
      // 部分浏览器不允许指定采样率，退回默认值后重采样
      audioContext = new AudioContext()
    }

    if (audioContext.state === 'suspended') {
      await audioContext.resume()
    }

    // 浏览器不保证遵循请求的采样率，以实际值为准
    const inputRate = audioContext.sampleRate
    if (inputRate !== SAMPLE_RATE) {
      log(`采集采样率为 ${inputRate}Hz，将重采样到 ${SAMPLE_RATE}Hz`, 'warning')
    }

    await audioContext.audioWorklet.addModule('/audio-recorder-processor.js')

    encoder = createOpusEncoder(mod)
    pendingSamples = new Float32Array(0)
    frameHandler = handler

    sourceNode = audioContext.createMediaStreamSource(mediaStream)
    workletNode = new AudioWorkletNode(audioContext, 'audio-recorder-processor', {
      numberOfInputs: 1,
      numberOfOutputs: 1,
      channelCount: CHANNELS
    })

    workletNode.port.onmessage = event => {
      if (event.data?.type !== 'audio-data') {
        return
      }
      pushSamples(resampleTo16k(new Float32Array(event.data.data), inputRate))
    }

    sourceNode.connect(workletNode)
    // 处理器不向 outputs 写数据，接到 destination 只为驱动音频图运行，不会外放麦克风声音
    workletNode.connect(audioContext.destination)

    capturing = true
    log('麦克风采集已启动', 'info')
  } catch (error) {
    await stopMicrophoneCapture()
    throw error
  }
}

/** 关闭麦克风并释放全部音频资源 */
export async function stopMicrophoneCapture(): Promise<void> {
  capturing = false
  frameHandler = null
  pendingSamples = new Float32Array(0)

  if (workletNode) {
    workletNode.port.onmessage = null
    workletNode.disconnect()
    workletNode = null
  }

  if (sourceNode) {
    sourceNode.disconnect()
    sourceNode = null
  }

  if (mediaStream) {
    mediaStream.getTracks().forEach(track => track.stop())
    mediaStream = null
  }

  if (audioContext) {
    try {
      await audioContext.close()
    } catch {
      // 上下文可能已关闭，忽略
    }
    audioContext = null
  }

  if (encoder) {
    encoder.destroy()
    encoder = null
  }
}
