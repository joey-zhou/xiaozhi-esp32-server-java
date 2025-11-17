import { Modal } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import type { ModalFuncProps } from 'ant-design-vue'

/**
 * 确认对话框 Composable
 * 统一管理各种确认操作
 */

export interface ConfirmOptions {
  /**
   * 标题
   */
  title?: string
  
  /**
   * 内容
   */
  content?: string
  
  /**
   * 确定按钮文本
   */
  okText?: string
  
  /**
   * 取消按钮文本
   */
  cancelText?: string
  
  /**
   * 确定按钮类型
   */
  okType?: 'primary' | 'danger' | 'default' | 'dashed' | 'link' | 'text'
  
  /**
   * 确定按钮加载状态
   */
  okButtonProps?: {
    loading?: boolean
    disabled?: boolean
  }
  
  /**
   * 图标
   */
  icon?: any
  
  /**
   * 宽度
   */
  width?: string | number
  
  /**
   * 是否显示取消按钮
   */
  showCancel?: boolean
}

export function useConfirm() {
  const { t } = useI18n()
  
  /**
   * 通用确认对话框
   */
  const confirm = (
    onOk: () => void | Promise<void>,
    options: ConfirmOptions = {}
  ) => {
    return Modal.confirm({
      title: options.title || t('common.confirm'),
      content: options.content || t('common.confirmOperation'),
      okText: options.okText || t('common.confirm'),
      cancelText: options.cancelText || t('common.cancel'),
      okType: options.okType || 'primary',
      icon: options.icon,
      width: options.width,
      okButtonProps: options.okButtonProps,
      class: options.showCancel === false ? 'hide-cancel-button' : undefined,
      onOk: async () => {
        await onOk()
      }
    })
  }
  
  /**
   * 删除确认
   */
  const confirmDelete = (
    onConfirm: () => void | Promise<void>,
    options: Partial<ConfirmOptions> = {}
  ) => {
    return Modal.confirm({
      title: options.title || t('common.confirmDelete'),
      content: options.content || t('common.confirmDeleteMessage'),
      okText: options.okText || t('common.delete'),
      cancelText: options.cancelText || t('common.cancel'),
      okType: 'danger',
      icon: options.icon,
      width: options.width,
      onOk: async () => {
        await onConfirm()
      }
    })
  }
  
  /**
   * 警告确认
   */
  const confirmWarning = (
    onConfirm: () => void | Promise<void>,
    options: Partial<ConfirmOptions> = {}
  ) => {
    return Modal.warning({
      title: options.title || t('common.warning'),
      content: options.content || t('common.warningMessage'),
      okText: options.okText || t('common.confirm'),
      okType: 'primary',
      icon: options.icon,
      width: options.width,
      onOk: async () => {
        await onConfirm()
      }
    } as ModalFuncProps)
  }
  
  /**
   * 信息确认
   */
  const confirmInfo = (
    onConfirm: () => void | Promise<void>,
    options: Partial<ConfirmOptions> = {}
  ) => {
    return Modal.info({
      title: options.title || t('common.info'),
      content: options.content || t('common.infoMessage'),
      okText: options.okText || t('common.confirm'),
      icon: options.icon,
      width: options.width,
      onOk: async () => {
        await onConfirm()
      }
    } as ModalFuncProps)
  }
  
  /**
   * 成功确认
   */
  const confirmSuccess = (
    onConfirm: () => void | Promise<void>,
    options: Partial<ConfirmOptions> = {}
  ) => {
    return Modal.success({
      title: options.title || t('common.success'),
      content: options.content || t('common.successMessage'),
      okText: options.okText || t('common.confirm'),
      icon: options.icon,
      width: options.width,
      onOk: async () => {
        await onConfirm()
      }
    } as ModalFuncProps)
  }
  
  /**
   * 错误确认
   */
  const confirmError = (
    onConfirm: () => void | Promise<void>,
    options: Partial<ConfirmOptions> = {}
  ) => {
    return Modal.error({
      title: options.title || t('common.error'),
      content: options.content || t('common.errorMessage'),
      okText: options.okText || t('common.confirm'),
      icon: options.icon,
      width: options.width,
      onOk: async () => {
        await onConfirm()
      }
    } as ModalFuncProps)
  }
  
  /**
   * 保存确认
   */
  const confirmSave = (
    onConfirm: () => void | Promise<void>,
    options: Partial<ConfirmOptions> = {}
  ) => {
    return Modal.confirm({
      title: options.title || t('common.confirmSave'),
      content: options.content || t('common.confirmSaveMessage'),
      okText: options.okText || t('common.save'),
      cancelText: options.cancelText || t('common.cancel'),
      okType: 'primary',
      icon: options.icon,
      width: options.width,
      onOk: async () => {
        await onConfirm()
      }
    })
  }
  
  /**
   * 取消确认
   */
  const confirmCancel = (
    onConfirm: () => void | Promise<void>,
    options: Partial<ConfirmOptions> = {}
  ) => {
    return Modal.confirm({
      title: options.title || t('common.confirmCancel'),
      content: options.content || t('common.confirmCancelMessage'),
      okText: options.okText || t('common.confirm'),
      cancelText: options.cancelText || t('common.cancel'),
      okType: 'danger',
      icon: options.icon,
      width: options.width,
      onOk: async () => {
        await onConfirm()
      }
    })
  }
  
  /**
   * 提交确认
   */
  const confirmSubmit = (
    onConfirm: () => void | Promise<void>,
    options: Partial<ConfirmOptions> = {}
  ) => {
    return Modal.confirm({
      title: options.title || t('common.confirmSubmit'),
      content: options.content || t('common.confirmSubmitMessage'),
      okText: options.okText || t('common.submit'),
      cancelText: options.cancelText || t('common.cancel'),
      okType: 'primary',
      icon: options.icon,
      width: options.width,
      onOk: async () => {
        await onConfirm()
      }
    })
  }
  
  return {
    confirm,
    confirmDelete,
    confirmWarning,
    confirmInfo,
    confirmSuccess,
    confirmError,
    confirmSave,
    confirmCancel,
    confirmSubmit
  }
}

