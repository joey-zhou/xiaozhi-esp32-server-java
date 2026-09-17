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
   * 确定按钮属性
   */
  okButtonProps?: {
    loading?: boolean
    disabled?: boolean
  }

  /**
   * 图标
   */
  icon?: ModalFuncProps['icon']

  /**
   * 宽度
   */
  width?: string | number

  /**
   * 是否显示取消按钮
   */
  showCancel?: boolean
}

/**
 * 各方法在 options 未给值时使用的兜底文案
 */
interface ConfirmDefaults {
  title: string
  content: string
  okText: string
  okType: NonNullable<ConfirmOptions['okType']>
}

export function useConfirm() {
  const { t } = useI18n()

  /**
   * 把 ConfirmOptions 折成 antd 的 ModalFuncProps
   * 只在字段为 undefined 时才套兜底值，显式传入的空串/false 必须原样保留
   */
  const toModalProps = (options: ConfirmOptions, defaults: ConfirmDefaults): ModalFuncProps => ({
    title: options.title ?? defaults.title,
    content: options.content ?? defaults.content,
    okText: options.okText ?? defaults.okText,
    cancelText: options.cancelText ?? t('common.cancel'),
    okType: options.okType ?? defaults.okType,
    okButtonProps: options.okButtonProps,
    icon: options.icon,
    width: options.width,
    okCancel: options.showCancel !== false,
  })

  /**
   * 执行确认回调
   * 回调抛错必须在这里吞掉：onOk 返回 rejected Promise 时 antd 只复位 loading 不关弹窗
   */
  const runAction = async (action: () => void | Promise<void>) => {
    try {
      await action()
    } catch (error) {
      console.error('confirm action failed:', error)
    }
  }

  /**
   * 通用确认对话框
   */
  const confirm = (onOk: () => void | Promise<void>, options: ConfirmOptions = {}) => {
    return Modal.confirm({
      ...toModalProps(options, {
        title: t('common.confirm'),
        content: t('common.confirmOperation'),
        okText: t('common.confirm'),
        okType: 'primary',
      }),
      onOk: () => runAction(onOk),
    })
  }

  /**
   * 删除等破坏性操作确认对话框，确定按钮默认红色
   */
  const confirmDelete = (onOk: () => void | Promise<void>, options: ConfirmOptions = {}) => {
    return Modal.confirm({
      ...toModalProps(options, {
        title: t('common.confirmDelete'),
        content: t('common.confirmDeleteMessage'),
        okText: t('common.delete'),
        okType: 'danger',
      }),
      onOk: () => runAction(onOk),
    })
  }

  /**
   * 确认对话框的 Promise 形态，确定返回 true、取消返回 false
   * 供调用方用「取消即中断后续流程」的写法
   */
  const confirmAsync = (options: ConfirmOptions = {}) => {
    return new Promise<boolean>((resolve) => {
      Modal.confirm({
        ...toModalProps(options, {
          title: t('common.confirm'),
          content: t('common.confirmOperation'),
          okText: t('common.confirm'),
          okType: 'primary',
        }),
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      })
    })
  }

  return {
    confirm,
    confirmAsync,
    confirmDelete,
  }
}
