import { ref } from 'vue'
import { message, type UploadProps } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { fileValidators } from '@/utils/fileValidators'
import { uploadFile } from '@/services/upload'

export interface UseAvatarUploadOptions {
  /**
   * 拿到可入库路径后的落地方式：既可以只是本地暂存（角色表单提交时再一起送出），
   * 也可以立即调接口持久化（账号设置页）。可以是异步的，loading 会等它跑完再熄灭
   */
  onUploaded: (avatarPath: string) => void | Promise<void>
}

/**
 * 头像上传 Composable
 *
 * 收敛「校验图片类型/大小 + 上传 + loading 态」这套在多个页面重复的逻辑，
 * 校验规则统一走 fileValidators.image，避免各页面各写一份 2MB/类型判断
 */
export function useAvatarUpload(options: UseAvatarUploadOptions) {
  const { t } = useI18n()

  const avatarLoading = ref(false)

  const beforeAvatarUpload: UploadProps['beforeUpload'] = (file) => {
    const result = fileValidators.image.validate(file)
    if (result !== true) {
      message.error(t(result))
      return false
    }

    avatarLoading.value = true
    uploadFile(file, 'avatar', { fullResponse: true })
      .then(res => options.onUploaded(res.relativePath || res.url))
      .catch(error => {
        message.error(`${t('common.avatarUploadFailed')}: ${error}`)
      })
      .finally(() => {
        avatarLoading.value = false
      })

    // a-upload 交给这里手动处理上传，返回 false 挡掉它自己的自动上传
    return false
  }

  return {
    /** <a-upload accept> 与校验规则共用同一份扩展名清单 */
    avatarAccept: fileValidators.image.accept,
    avatarLoading,
    beforeAvatarUpload,
  }
}
