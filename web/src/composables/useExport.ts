import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

/**
 * 数据导出 Composable
 * 支持 CSV、JSON、Excel 等格式导出
 */

export interface ExportColumn<T = unknown> {
  /**
   * 列键名
   */
  key: string
  
  /**
   * 列标题
   */
  title: string
  
  /**
   * 自定义格式化函数
   */
  format?: (value: unknown, record: T) => string | number
}

export interface ExportOptions<T = unknown> {
  /**
   * 文件名（不含扩展名）
   */
  filename?: string
  
  /**
   * 列配置（如果不指定，则导出所有字段）
   */
  columns?: ExportColumn<T>[]
  
  /**
   * 是否显示加载提示
   */
  showLoading?: boolean
}

export function useExport() {
  const { t } = useI18n()
  
  // 导出状态
  const exporting = ref(false)
  
  /**
   * 转换为 CSV 格式
   */
  const convertToCSV = <T>(data: T[], columns?: ExportColumn<T>[]): string => {
    if (data.length === 0) return ''
    
    // 如果没有指定列，使用第一行的所有键
    const firstItem = data[0] as object
    const cols: ExportColumn<T>[] = columns || Object.keys(firstItem).map(key => ({
      key,
      title: key,
    }))
    
    // CSV 头部
    const headers = cols.map(col => `"${col.title}"`).join(',')
    
    // CSV 数据行
    const rows = data.map(record => {
      const recordObj = record as { [key: string]: unknown }
      return cols.map(col => {
        let value: unknown = recordObj[col.key]
        
        // 使用自定义格式化
        if (col.format) {
          value = col.format(value, record)
        }
        
        // 处理特殊字符
        if (value === null || value === undefined) {
          return '""'
        }
        
        // 转为字符串并转义引号
        const strValue = String(value).replace(/"/g, '""')
        return `"${strValue}"`
      }).join(',')
    })
    
    return [headers, ...rows].join('\n')
  }
  
  /**
   * 下载文件
   */
  const downloadFile = (content: string, filename: string, mimeType: string) => {
    // 添加 BOM 使 Excel 正确识别 UTF-8
    const BOM = '\uFEFF'
    const blob = new Blob([BOM + content], { type: `${mimeType};charset=utf-8;` })
    const url = URL.createObjectURL(blob)
    
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.style.display = 'none'
    
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    
    // 释放 URL 对象
    setTimeout(() => URL.revokeObjectURL(url), 100)
  }
  
  /**
   * 导出为 CSV
   */
  const exportToCSV = async <T>(
    data: T[],
    options: ExportOptions<T> = {}
  ): Promise<boolean> => {
    if (data.length === 0) {
      message.warning(t('export.noData'))
      return false
    }
    
    exporting.value = true
    
    try {
      if (options.showLoading !== false) {
        message.loading(t('export.exporting'))
      }
      
      const csv = convertToCSV(data, options.columns)
      const filename = `${options.filename || 'export'}.csv`
      
      downloadFile(csv, filename, 'text/csv')
      
      // 只在启用内部提示时显示成功消息
      if (options.showLoading !== false) {
        message.success(t('export.success'))
      }
      return true
    } catch (error) {
      console.error('CSV 导出失败:', error)
      // 只在启用内部提示时显示错误消息
      if (options.showLoading !== false) {
        message.error(t('export.failed'))
      }
      return false
    } finally {
      exporting.value = false
    }
  }
  
  /**
   * 导出为 JSON
   */
  const exportToJSON = async <T>(
    data: T[],
    options: ExportOptions<T> = {}
  ): Promise<boolean> => {
    if (data.length === 0) {
      message.warning(t('export.noData'))
      return false
    }
    
    exporting.value = true
    
    try {
      if (options.showLoading !== false) {
        message.loading(t('export.exporting'))
      }
      
      // 如果指定了列，只导出指定的字段
      let exportData = data
      if (options.columns && options.columns.length > 0) {
        exportData = data.map(record => {
          const recordObj = record as { [key: string]: unknown }
          const newRecord: { [key: string]: unknown } = {}
          options.columns?.forEach(col => {
            let value = recordObj[col.key]
            if (col.format) {
              value = col.format(value, record)
            }
            newRecord[col.title || col.key] = value
          })
          return newRecord as T
        })
      }
      
      const json = JSON.stringify(exportData, null, 2)
      const filename = `${options.filename || 'export'}.json`
      
      downloadFile(json, filename, 'application/json')
      
      // 只在启用内部提示时显示成功消息
      if (options.showLoading !== false) {
        message.success(t('export.success'))
      }
      return true
    } catch (error) {
      console.error('JSON 导出失败:', error)
      // 只在启用内部提示时显示错误消息
      if (options.showLoading !== false) {
        message.error(t('export.failed'))
      }
      return false
    } finally {
      exporting.value = false
    }
  }
  
  /**
   * 导出为 Excel（使用 CSV 格式，Excel 可以打开）
   */
  const exportToExcel = async <T>(
    data: T[],
    options: ExportOptions<T> = {}
  ): Promise<boolean> => {
    if (data.length === 0) {
      message.warning(t('export.noData'))
      return false
    }
    
    exporting.value = true
    
    try {
      if (options.showLoading !== false) {
        message.loading(t('export.exporting'))
      }
      
      const csv = convertToCSV(data, options.columns)
      const filename = `${options.filename || 'export'}.xlsx`
      
      // 使用 UTF-16LE 编码和特殊格式让 Excel 识别
      const BOM = '\ufeff'
      const csvWithBOM = BOM + csv
      
      // 创建 Excel 兼容的 CSV
      const blob = new Blob([csvWithBOM], { 
        type: 'application/vnd.ms-excel;charset=utf-8;' 
      })
      const url = URL.createObjectURL(blob)
      
      const link = document.createElement('a')
      link.href = url
      link.download = filename
      link.style.display = 'none'
      
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      
      setTimeout(() => URL.revokeObjectURL(url), 100)
      
      // 只在启用内部提示时显示成功消息
      if (options.showLoading !== false) {
        message.success(t('export.success'))
      }
      return true
    } catch (error) {
      console.error('Excel 导出失败:', error)
      // 只在启用内部提示时显示错误消息
      if (options.showLoading !== false) {
        message.error(t('export.failed'))
      }
      return false
    } finally {
      exporting.value = false
    }
  }
  
  /**
   * 自动选择导出格式
   */
  const exportData = async <T>(
    data: T[],
    format: 'csv' | 'json' | 'excel',
    options: ExportOptions<T> = {}
  ): Promise<boolean> => {
    switch (format) {
      case 'csv':
        return await exportToCSV(data, options)
      case 'json':
        return await exportToJSON(data, options)
      case 'excel':
        return await exportToExcel(data, options)
      default:
        message.error(t('export.unsupportedFormat'))
        return false
    }
  }
  
  /**
   * 从 CSV 文本解析为 HTML 表格（用于预览）
   */
  const parseCSVToTable = (csvText: string): string => {
    if (!csvText.trim()) {
      return ''
    }
    
    const lines = csvText.split(/\r?\n/).filter(line => line.trim())
    if (lines.length === 0) {
      return ''
    }
    
    const headerRow = lines[0]
    const dataRows = lines.slice(1)
    
    // 简单的 CSV 解析（处理引号）
    const parseCSVRow = (row: string): string[] => {
      const values: string[] = []
      let current = ''
      let inQuotes = false
      
      for (let i = 0; i < row.length; i++) {
        const char = row[i]
        const nextChar = row[i + 1]
        
        if (char === '"') {
          if (inQuotes && nextChar === '"') {
            current += '"'
            i++
          } else {
            inQuotes = !inQuotes
          }
        } else if (char === ',' && !inQuotes) {
          values.push(current.trim())
          current = ''
        } else {
          current += char
        }
      }
      
      values.push(current)
      return values
    }
    
    const headers = parseCSVRow(headerRow || '')
    const headerHTML = `<tr>${headers.map(h => `<th>${h}</th>`).join('')}</tr>`
    
    const rowsHTML = dataRows.map(row => {
      const cells = parseCSVRow(row)
      return `<tr>${cells.map(c => `<td>${c}</td>`).join('')}</tr>`
    }).join('')
    
    return `<thead>${headerHTML}</thead><tbody>${rowsHTML}</tbody>`
  }
  
  /**
   * 从剪贴板导入 CSV 数据
   */
  const importFromClipboard = async <T = { [key: string]: string }>(): Promise<T[]> => {
    try {
      const text = await navigator.clipboard.readText()
      
      if (!text.trim()) {
        message.warning(t('export.noData'))
        return []
      }
      
      // 简单解析 CSV（假设以 tab 或逗号分隔）
      const lines = text.split(/\r?\n/).filter(line => line.trim())
      const headers = lines[0]?.split(/\t|,/).map(h => h.trim()) || []
      
      const data: T[] = []
      for (let i = 1; i < lines.length; i++) {
        const values = lines[i]?.split(/\t|,/).map(v => v.trim()) || []
        const row: { [key: string]: string } = {}
        headers.forEach((header, index) => {
          row[header] = values[index] || ''
        })
        data.push(row as T)
      }
      
      message.success(t('export.importSuccess'))
      return data
    } catch (error) {
      console.error('导入失败:', error)
      message.error(t('export.importFailed'))
      return []
    }
  }
  
  return {
    // 状态
    exporting,
    
    // 方法
    exportToCSV,
    exportToJSON,
    exportToExcel,
    exportData,
    parseCSVToTable,
    importFromClipboard,
    convertToCSV,
    downloadFile
  }
}
