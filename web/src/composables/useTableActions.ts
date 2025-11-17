import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { useConfirm } from './useConfirm'
import { useClipboard } from './useClipboard'
import type { Ref } from 'vue'

/**
 * 表格操作 Composable
 * 统一管理表格的增删改查操作
 */

export interface UseTableActionsOptions<T = any> {
  /**
   * 编辑回调
   */
  onEdit?: (record: T) => void | Promise<void>
  
  /**
   * 查看回调
   */
  onView?: (record: T) => void | Promise<void>
  
  /**
   * 删除回调
   */
  onDelete?: (record: T) => Promise<boolean | void>
  
  /**
   * 下载回调
   */
  onDownload?: (record: T) => void | Promise<void>
  
  /**
   * 复制回调
   */
  onCopy?: (record: T, text: string) => void | Promise<void>
  
  /**
   * 设为默认回调
   */
  onSetDefault?: (record: T) => Promise<boolean | void>
  
  /**
   * 删除成功后是否自动刷新数据
   */
  autoRefresh?: boolean
  
  /**
   * 刷新数据的函数
   */
  refreshData?: () => void | Promise<void>
  
  /**
   * 获取复制文本的函数
   */
  getCopyText?: (record: T) => string
}

export function useTableActions<T = any>(options: UseTableActionsOptions<T> = {}) {
  const { t } = useI18n()
  const { confirmDelete } = useConfirm()
  const { copy } = useClipboard()
  
  /**
   * 处理编辑
   */
  const handleEdit = async (record: T) => {
    await options.onEdit?.(record)
  }
  
  /**
   * 处理查看
   */
  const handleView = async (record: T) => {
    await options.onView?.(record)
  }
  
  /**
   * 处理删除
   */
  const handleDelete = async (record: T) => {
    if (!options.onDelete) {
      console.warn('未配置 onDelete 回调')
      return
    }
    
    await confirmDelete(async () => {
      const result = await options.onDelete!(record)
      
      // 如果返回 false，表示删除失败，不刷新数据
      if (result === false) {
        return
      }
      
      // 自动刷新数据
      if (options.autoRefresh !== false && options.refreshData) {
        await options.refreshData()
      }
    })
  }
  
  /**
   * 处理下载
   */
  const handleDownload = async (record: T) => {
    await options.onDownload?.(record)
  }
  
  /**
   * 处理复制
   */
  const handleCopy = async (record: T) => {
    const text = options.getCopyText?.(record) || JSON.stringify(record, null, 2)
    const success = await copy(text)
    
    if (success) {
      await options.onCopy?.(record, text)
    }
  }
  
  /**
   * 处理设为默认
   */
  const handleSetDefault = async (record: T) => {
    if (!options.onSetDefault) {
      console.warn('未配置 onSetDefault 回调')
      return
    }
    
    const result = await options.onSetDefault(record)
    
    // 如果返回 false，表示操作失败，不刷新数据
    if (result === false) {
      return
    }
    
    // 自动刷新数据
    if (options.autoRefresh !== false && options.refreshData) {
      await options.refreshData()
    }
  }
  
  /**
   * 批量删除
   */
  const handleBatchDelete = async (records: T[]) => {
    if (!options.onDelete) {
      console.warn('未配置 onDelete 回调')
      return
    }
    
    if (records.length === 0) {
      message.warning(t('common.pleaseSelectData'))
      return
    }
    
    await confirmDelete(async () => {
      let successCount = 0
      let failCount = 0
      
      for (const record of records) {
        try {
          const result = await options.onDelete!(record)
          if (result !== false) {
            successCount++
          } else {
            failCount++
          }
        } catch (error) {
          failCount++
          console.error('删除失败:', error)
        }
      }
      
      // 显示结果
      if (successCount > 0) {
        message.success(t('common.batchDeleteSuccess', { count: successCount }))
      }
      if (failCount > 0) {
        message.error(t('common.batchDeleteFailed', { count: failCount }))
      }
      
      // 自动刷新数据
      if (options.autoRefresh !== false && options.refreshData) {
        await options.refreshData()
      }
    }, {
      title: t('common.confirmBatchDelete'),
      content: t('common.confirmBatchDeleteMessage', { count: records.length })
    })
  }
  
  return {
    handleEdit,
    handleView,
    handleDelete,
    handleDownload,
    handleCopy,
    handleSetDefault,
    handleBatchDelete
  }
}

