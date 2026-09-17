import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { message } from 'ant-design-vue'

import { useDragUpload } from '../useDragUpload'
import type { DragUploadOptions } from '../useDragUpload'

// Helper: 构造带 dataTransfer 的拖拽事件（jsdom 没有可用的 DragEvent 构造器）
function dispatchDragEvent(
  type: 'dragenter' | 'dragover' | 'dragleave' | 'drop',
  dataTransfer: Record<string, unknown> | null = { types: ['Files'] },
) {
  const event = new Event(type, { bubbles: true, cancelable: true })
  Object.defineProperty(event, 'dataTransfer', { value: dataTransfer })
  document.dispatchEvent(event)
}

function createMockFile(name: string, type = 'audio/wav'): File {
  return new File([], name, { type })
}

// Helper: 在组件内挂载 composable，让 onMounted 里的监听器真正装上
function mountDragUpload(options: DragUploadOptions) {
  let api!: ReturnType<typeof useDragUpload>
  const wrapper = mount(defineComponent({
    setup() {
      api = useDragUpload(options)
      return () => null
    },
  }))
  return { api, wrapper }
}

describe('useDragUpload', () => {
  let wrappers: { unmount: () => void }[] = []

  beforeEach(() => {
    vi.clearAllMocks()
    wrappers = []
  })

  afterEach(() => {
    wrappers.forEach(w => w.unmount())
  })

  function mountAndTrack(options: DragUploadOptions) {
    const mounted = mountDragUpload(options)
    wrappers.push(mounted.wrapper)
    return mounted
  }

  describe('handleDrop 提示', () => {
    const passingValidator = { validate: () => true as const }

    it('onDrop 返回 false 时不弹成功提示', async () => {
      const onDrop = vi.fn(() => false)
      mountAndTrack({
        validator: passingValidator,
        onDrop,
        messages: { successMessage: 'common.uploadSuccess' },
      })

      dispatchDragEvent('drop', { files: { 0: createMockFile('a.m4a', 'audio/x-m4a'), length: 1 } })
      await flushPromises()

      expect(onDrop).toHaveBeenCalledTimes(1)
      expect(message.success).not.toHaveBeenCalled()
      expect(message.error).not.toHaveBeenCalled()
    })

    it('onDrop 异步返回 false 时不弹成功提示', async () => {
      const onDrop = vi.fn(async () => false)
      mountAndTrack({
        validator: passingValidator,
        onDrop,
        messages: { successMessage: 'common.uploadSuccess' },
      })

      dispatchDragEvent('drop', { files: { 0: createMockFile('a.wav'), length: 1 } })
      await flushPromises()

      expect(message.success).not.toHaveBeenCalled()
    })

    it('onDrop 未返回 false 时弹成功提示', async () => {
      const onDrop = vi.fn()
      mountAndTrack({
        validator: passingValidator,
        onDrop,
        messages: { successMessage: 'common.uploadSuccess' },
      })

      dispatchDragEvent('drop', { files: { 0: createMockFile('a.wav'), length: 1 } })
      await flushPromises()

      expect(message.success).toHaveBeenCalledWith('common.uploadSuccess')
    })

    it('校验失败时弹 i18n key 且不调用 onDrop', async () => {
      const onDrop = vi.fn()
      mountAndTrack({
        validator: { validate: () => 'common.audioFormatError' },
        onDrop,
        messages: { successMessage: 'common.uploadSuccess' },
      })

      dispatchDragEvent('drop', { files: { 0: createMockFile('a.txt', 'text/plain'), length: 1 } })
      await flushPromises()

      expect(onDrop).not.toHaveBeenCalled()
      expect(message.error).toHaveBeenCalledWith('common.audioFormatError')
      expect(message.success).not.toHaveBeenCalled()
    })

    it('onDrop 抛异常时弹错误提示且不弹成功提示', async () => {
      mountAndTrack({
        validator: passingValidator,
        onDrop: () => { throw new Error('boom') },
        messages: { successMessage: 'common.uploadSuccess' },
      })

      dispatchDragEvent('drop', { files: { 0: createMockFile('a.wav'), length: 1 } })
      await flushPromises()

      expect(message.error).toHaveBeenCalledWith('boom')
      expect(message.success).not.toHaveBeenCalled()
    })
  })

  describe('enabled 翻转时的遮罩计数', () => {
    it('拖拽途中 enabled 由 true 翻成 false，遮罩仍能消失且计数不变负', async () => {
      const activeTab = ref('2')
      const { api } = mountAndTrack({
        validator: { validate: () => true as const },
        onDrop: vi.fn(),
        enabled: () => activeTab.value === '2',
      })

      dispatchDragEvent('dragenter')
      expect(api.isDragging.value).toBe(true)

      // 拖拽途中切走 Tab
      activeTab.value = '1'
      dispatchDragEvent('dragleave')
      expect(api.isDragging.value).toBe(false)

      // 切回来后遮罩必须还能正常显示与消失（计数若变负则永远回不到 0）
      activeTab.value = '2'
      dispatchDragEvent('dragenter')
      expect(api.isDragging.value).toBe(true)

      dispatchDragEvent('dragleave')
      expect(api.isDragging.value).toBe(false)
    })

    it('enabled 为 false 时进入不显示遮罩，且离开后计数回到 0', () => {
      const enabled = ref(false)
      const { api } = mountAndTrack({
        validator: { validate: () => true as const },
        onDrop: vi.fn(),
        enabled: () => enabled.value,
      })

      dispatchDragEvent('dragenter')
      expect(api.isDragging.value).toBe(false)

      // 计数在 enabled 为 false 时也要递减，否则下一轮 enter/leave 会失衡
      enabled.value = true
      dispatchDragEvent('dragleave')
      expect(api.isDragging.value).toBe(false)

      dispatchDragEvent('dragenter')
      expect(api.isDragging.value).toBe(true)
      dispatchDragEvent('dragleave')
      expect(api.isDragging.value).toBe(false)
    })

    it('嵌套元素造成的多层 enter 只在计数归零时收起遮罩', () => {
      const { api } = mountAndTrack({
        validator: { validate: () => true as const },
        onDrop: vi.fn(),
      })

      dispatchDragEvent('dragenter')
      dispatchDragEvent('dragenter')
      expect(api.isDragging.value).toBe(true)

      dispatchDragEvent('dragleave')
      expect(api.isDragging.value).toBe(true)

      dispatchDragEvent('dragleave')
      expect(api.isDragging.value).toBe(false)
    })

    it('enabled 翻成 false 后 dragover 立刻收起遮罩', () => {
      const enabled = ref(true)
      const { api } = mountAndTrack({
        validator: { validate: () => true as const },
        onDrop: vi.fn(),
        enabled: () => enabled.value,
      })

      dispatchDragEvent('dragenter')
      expect(api.isDragging.value).toBe(true)

      enabled.value = false
      dispatchDragEvent('dragover')
      expect(api.isDragging.value).toBe(false)
    })
  })

  describe('提示文本', () => {
    it('未配置 dragText 时用默认 key，配置后用配置的 key', () => {
      const { api } = mountAndTrack({
        validator: { validate: () => true as const },
        onDrop: vi.fn(),
      })
      expect(api.dragText.value).toBe('common.dragDropFile')
      expect(api.dragHint.value).toBe('')

      const custom = mountAndTrack({
        validator: { validate: () => true as const },
        onDrop: vi.fn(),
        messages: { dragText: 'common.dragDropText', dragHint: 'common.dragDropHint' },
      })
      expect(custom.api.dragText.value).toBe('common.dragDropText')
      expect(custom.api.dragHint.value).toBe('common.dragDropHint')
    })
  })
})
