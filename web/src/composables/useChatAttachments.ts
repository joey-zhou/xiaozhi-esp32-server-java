import { computed, onBeforeUnmount, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { uploadChatAttachment } from '@/services/chat'
import { fitImage } from '@/utils/image'
import { useRequest } from '@/composables/useRequest'
import { CHAT_ATTACHMENT_MAX_COUNT, CHAT_IMAGE_MAX_BYTES, CHAT_IMAGE_MAX_EDGE } from '@/constants/api'
import type { ChatDraftAttachment } from '@/types/chat'

/**
 * 输入框里的附件：选好就上传，发消息时取走已传好的；本地预览地址留给消息气泡，页面卸载时释放
 */
export function useChatAttachments() {
  const { t } = useI18n()
  const { executeFull } = useRequest()
  const attachments = ref<ChatDraftAttachment[]>([])
  const previewUrls: string[] = []
  let nextLocalId = 0

  const uploading = computed(() => attachments.value.some((a) => a.status === 'uploading'))
  const hasReady = computed(() => attachments.value.some((a) => a.status === 'ready'))

  async function add(files: File[]) {
    const images = files.filter((file) => file.type.startsWith('image/'))
    const room = CHAT_ATTACHMENT_MAX_COUNT - attachments.value.length
    if (images.length > room) {
      message.warning(t('chat.attachmentLimit', { count: CHAT_ATTACHMENT_MAX_COUNT }))
    }
    await Promise.all(images.slice(0, Math.max(room, 0)).map(upload))
  }

  async function upload(file: File) {
    let fitted: File
    try {
      fitted = await fitImage(file, CHAT_IMAGE_MAX_EDGE, CHAT_IMAGE_MAX_BYTES)
    } catch {
      message.error(t('chat.attachmentUnreadable', { name: file.name }))
      return
    }
    const previewUrl = URL.createObjectURL(fitted)
    previewUrls.push(previewUrl)
    const localId = ++nextLocalId
    attachments.value.push({ localId, name: file.name, previewUrl, status: 'uploading' })

    const { ok, data } = await executeFull(() => uploadChatAttachment(fitted))
    const index = attachments.value.findIndex((a) => a.localId === localId)
    if (index < 0) return
    if (ok && data) {
      attachments.value[index] = { ...attachments.value[index]!, status: 'ready', attachmentId: data.attachmentId }
    } else {
      attachments.value.splice(index, 1)
    }
  }

  function remove(localId: number) {
    attachments.value = attachments.value.filter((a) => a.localId !== localId)
  }

  /** 取走已传好的附件去发送 */
  function takeReady(): ChatDraftAttachment[] {
    const ready = attachments.value.filter((a) => a.status === 'ready')
    attachments.value = attachments.value.filter((a) => a.status !== 'ready')
    return ready
  }

  onBeforeUnmount(() => {
    previewUrls.forEach((url) => URL.revokeObjectURL(url))
  })

  return { attachments, uploading, hasReady, add, remove, takeReady }
}
