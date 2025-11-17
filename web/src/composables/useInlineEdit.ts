import { ref, type Ref } from 'vue'

/**
 * 行内编辑 Composable
 * 用于表格行内编辑功能
 */

export interface UseInlineEditOptions<T> {
  /**
   * 获取项的唯一标识
   * @param item 数据项
   * @returns 唯一标识（通常是 ID）
   */
  getKey: (item: T) => string | number
  
  /**
   * 保存回调函数
   * @param item 编辑后的数据项
   * @returns 是否成功，返回 false 时不退出编辑模式
   */
  onSave?: (item: T) => Promise<boolean | void>
  
  /**
   * 取消编辑回调
   * @param item 取消编辑的数据项
   */
  onCancel?: (item: T) => void
}

export interface EditableItem {
  editable?: boolean
  [key: string]: any
}

export function useInlineEdit<T extends EditableItem>(
  dataSource: Ref<T[]>,
  options: UseInlineEditOptions<T>
) {
  // 当前编辑的 key
  const editingKey = ref<string | number>('')
  
  // 缓存数据（用于取消编辑时恢复）
  const cacheData = ref<T[]>([])
  
  /**
   * 判断是否正在编辑某项
   */
  const isEditing = (key: string | number): boolean => {
    return editingKey.value === key
  }
  
  /**
   * 开始编辑
   * @param key 要编辑项的 key
   */
  const startEdit = (key: string | number) => {
    // 取消其他行的编辑状态
    dataSource.value.forEach(item => {
      if (item.editable) {
        item.editable = false
      }
    })
    
    // 开始编辑目标行
    const target = dataSource.value.find(item => options.getKey(item) === key)
    if (target) {
      // 备份当前数据
      cacheData.value = dataSource.value.map(item => ({ ...item }))
      
      // 设置编辑状态
      target.editable = true
      editingKey.value = key
    }
  }
  
  /**
   * 取消编辑
   * @param key 要取消编辑项的 key
   */
  const cancelEdit = (key: string | number) => {
    const target = dataSource.value.find(item => options.getKey(item) === key)
    const cache = cacheData.value.find(item => options.getKey(item as T) === key)
    
    if (target && cache) {
      // 恢复缓存数据
      Object.assign(target, cache)
      target.editable = false
    }
    
    editingKey.value = ''
    
    // 触发取消回调
    if (target) {
      options.onCancel?.(target)
    }
  }
  
  /**
   * 保存编辑
   * @param item 编辑后的数据项
   */
  const saveEdit = async (item: T): Promise<boolean> => {
    if (!options.onSave) {
      item.editable = false
      editingKey.value = ''
      return true
    }
    
    try {
      const result = await options.onSave(item)
      
      // 如果返回 false，不退出编辑模式
      if (result === false) {
        return false
      }
      
      item.editable = false
      editingKey.value = ''
      return true
    } catch (error) {
      console.error('保存失败:', error)
      return false
    }
  }
  
  /**
   * 更新字段值
   * @param key 项的 key
   * @param field 字段名
   * @param value 新值
   */
  const updateField = <K extends keyof T>(
    key: string | number,
    field: K,
    value: T[K]
  ) => {
    const target = dataSource.value.find(item => options.getKey(item) === key)
    if (target) {
      target[field] = value
    }
  }
  
  return {
    // 状态
    editingKey,
    cacheData,
    
    // 方法
    isEditing,
    startEdit,
    cancelEdit,
    saveEdit,
    updateField
  }
}

