import { describe, it, expect, vi, afterEach } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

import { useMediaRecorder, type UseMediaRecorderReturn } from '../useMediaRecorder'

const TEST_CONFIG = {
  sampleRate: 16000,
  channels: 1,
  bitDepth: 16,
  maxDuration: 60,
}

/** 在组件里挂载 composable，让 onBeforeUnmount 真正装上 */
function mountRecorder(onRecorded: (file: File) => void) {
  let api!: UseMediaRecorderReturn
  const wrapper = mount(defineComponent({
    setup() {
      api = useMediaRecorder({ config: TEST_CONFIG, onRecorded })
      return () => null
    },
  }))
  return { api, wrapper }
}

/** 捕获 AudioWorkletNode 的实例，测试里用它模拟采集到的音频消息 */
function stubAudioGraph() {
  let createdWorkletNode: {
    port: { onmessage: ((event: { data: { type: string; data: ArrayBuffer } } ) => void) | null }
    connect: ReturnType<typeof vi.fn>
    disconnect: ReturnType<typeof vi.fn>
  } | null = null

  vi.stubGlobal('AudioContext', class {
    sampleRate = TEST_CONFIG.sampleRate
    state = 'running'
    destination = {}
    audioWorklet = { addModule: vi.fn().mockResolvedValue(undefined) }
    createMediaStreamSource() {
      return { connect: vi.fn(), disconnect: vi.fn() }
    }
    createAnalyser() {
      return { fftSize: 0, connect: vi.fn(), disconnect: vi.fn() }
    }
    close = vi.fn()
  })

  class FakeWorkletNode {
    port: { onmessage: ((event: { data: { type: string; data: ArrayBuffer } } ) => void) | null } = { onmessage: null }
    connect = vi.fn()
    disconnect = vi.fn()
  }

  // 用 Proxy 在外部截获实例，构造器里不把 this 存到外部变量
  vi.stubGlobal('AudioWorkletNode', new Proxy(FakeWorkletNode, {
    construct(target, args) {
      createdWorkletNode = Reflect.construct(target, args) as FakeWorkletNode
      return createdWorkletNode
    },
  }))

  vi.stubGlobal('MediaRecorder', class {
    state = 'recording'
    ondataavailable: unknown = null
    start = vi.fn()
    stop = vi.fn()
  })

  return {
    getCreatedWorkletNode: () => createdWorkletNode,
  }
}

describe('useMediaRecorder', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('停止录音后释放麦克风轨道，并把编码好的文件回调出去', async () => {
    const track = { stop: vi.fn() }
    const stream = { getTracks: () => [track] }
    const { getCreatedWorkletNode } = stubAudioGraph()

    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn().mockResolvedValue(stream) },
    })

    const onRecorded = vi.fn()
    const { api, wrapper } = mountRecorder(onRecorded)

    api.startRecording()
    await flushPromises()
    expect(api.isRecording.value).toBe(true)

    // 模拟 AudioWorklet 采集到一帧样本
    getCreatedWorkletNode()?.port.onmessage?.({
      data: { type: 'audio-data', data: new Float32Array(960).buffer },
    })

    api.stopRecording()

    expect(track.stop).toHaveBeenCalledTimes(1)
    expect(api.isRecording.value).toBe(false)
    expect(onRecorded).toHaveBeenCalledTimes(1)
    expect(onRecorded.mock.calls[0]![0]).toBeInstanceOf(File)

    wrapper.unmount()
  })

  it('权限被拒时报出对应错误，且不会产生录音文件', async () => {
    stubAudioGraph()

    const deniedError = Object.assign(new Error('denied'), { name: 'NotAllowedError' })
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn().mockRejectedValue(deniedError) },
    })

    const onRecorded = vi.fn()
    const { api, wrapper } = mountRecorder(onRecorded)

    api.startRecording()
    await flushPromises()

    expect(api.recordError.value).toBe('common.microphoneAccessDenied')
    expect(api.isRecording.value).toBe(false)
    expect(onRecorded).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('组件卸载时兜底释放麦克风轨道', async () => {
    const track = { stop: vi.fn() }
    const stream = { getTracks: () => [track] }
    stubAudioGraph()

    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn().mockResolvedValue(stream) },
    })

    const onRecorded = vi.fn()
    const { api, wrapper } = mountRecorder(onRecorded)

    api.startRecording()
    await flushPromises()
    expect(api.isRecording.value).toBe(true)

    // 没手动停止就直接卸载：麦克风必须照样被释放
    wrapper.unmount()

    expect(track.stop).toHaveBeenCalledTimes(1)
  })
})
