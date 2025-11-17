import { ref, type Ref } from 'vue'
import type { FormInstance } from 'ant-design-vue'

/**
 * 弹窗管理 Composable
 * 用于统一管理创建/编辑弹窗的状态和逻辑
 */

export interface UseModalOptions<T = any> {
  /**
   * 提交回调函数
   * @param data 表单数据
   * @param isEdit 是否为编辑模式
   * @returns 是否成功，返回 false 时不关闭弹窗
   */
  onSubmit?: (data: T, isEdit: boolean) => Promise<boolean | void>
  
  /**
   * 打开弹窗回调
   * @param item 编辑项（编辑模式时传入）
   */
  onOpen?: (item?: T) => void | Promise<void>
  
  /**
   * 关闭弹窗回调
   */
  onClose?: () => void
  
  /**
   * 表单实例引用（用于重置表单）
   */
  formRef?: Ref<FormInstance | undefined>
}

export function useModal<T = any>(options?: UseModalOptions<T>) {
  // 弹窗可见性
  const visible = ref(false)
  
  // 是否为编辑模式
  const isEdit = ref(false)
  
  // 当前编辑的项
  const editingItem = ref<T | null>(null)
  
  // 提交加载状态
  const submitLoading = ref(false)
  
  /**
   * 打开弹窗 - 创建模式
   */
  const openCreate = async () => {
    isEdit.value = false
    editingItem.value = null
    await options?.onOpen?.()
    visible.value = true
  }
  
  /**
   * 打开弹窗 - 编辑模式
   * @param item 要编辑的项
   */
  const openEdit = async (item: T) => {
    isEdit.value = true
    editingItem.value = item
    await options?.onOpen?.(item)
    visible.value = true
  }
  
  /**
   * 关闭弹窗
   */
  const close = () => {
    visible.value = false
    editingItem.value = null
    
    // 重置表单
    if (options?.formRef?.value) {
      options.formRef.value.resetFields()
    }
    
    options?.onClose?.()
  }
  
  /**
   * 提交表单
   * @param data 表单数据
   */
  const submit = async (data: T): Promise<boolean> => {
    if (!options?.onSubmit) {
      close()
      return true
    }
    
    submitLoading.value = true
    try {
      const result = await options.onSubmit(data, isEdit.value)
      
      // 如果返回 false，不关闭弹窗
      if (result === false) {
        return false
      }
      
      close()
      return true
    } catch (error) {
      console.error('提交失败:', error)
      return false
    } finally {
      submitLoading.value = false
    }
  }
  
  /**
   * 取消操作（与 close 相同，但语义更明确）
   */
  const cancel = () => {
    close()
  }
  
  return {
    // 状态
    visible,
    isEdit,
    editingItem,
    submitLoading,
    
    // 方法
    openCreate,
    openEdit,
    close,
    cancel,
    submit
  }
}

