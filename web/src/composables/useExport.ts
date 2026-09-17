import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

/**
 * 数据导出 Composable
 * 支持 CSV、Excel 导出
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
  format?(value: unknown, record: T): string | number
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

/** 以这些字符开头的文本会被 Excel/WPS 当公式执行 */
const CSV_FORMULA_PREFIX = /^[=+\-@\t\r]/

/**
 * 转义单个 CSV 单元格：公式前缀加单引号强制成文本，引号翻倍，整体加引号
 * 纯数字（含负数）不加单引号，避免把数值列变成文本
 */
function escapeCSVCell(value: unknown): string {
  if (value === null || value === undefined) {
    return '""'
  }
  const raw = String(value)
  const safe = CSV_FORMULA_PREFIX.test(raw) && Number.isNaN(Number(raw)) ? `'${raw}` : raw
  return `"${safe.replace(/"/g, '""')}"`
}

/**
 * 转换为 CSV 格式
 * 纯函数，独立导出供单测直接调用；组件侧统一走 exportToCSV
 */
export function convertToCSV<T>(data: T[], columns?: ExportColumn<T>[]): string {
  if (data.length === 0) return ''

  // 如果没有指定列，使用第一行的所有键
  const firstItem = data[0] as object
  const cols: ExportColumn<T>[] = columns || Object.keys(firstItem).map(key => ({
    key,
    title: key,
  }))

  // CSV 头部
  const headers = cols.map(col => escapeCSVCell(col.title)).join(',')

  // CSV 数据行
  const rows = data.map(record => {
    const recordObj = record as { [key: string]: unknown }
    return cols.map(col => {
      let value: unknown = recordObj[col.key]

      // 使用自定义格式化
      if (col.format) {
        value = col.format(value, record)
      }

      return escapeCSVCell(value)
    }).join(',')
  })

  return [headers, ...rows].join('\n')
}

/**
 * 触发浏览器下载：造一个隐藏的 <a download> 元素点击后立即回收
 * withBom 只给 CSV 用：Excel 靠 BOM 认 UTF-8，而 JSON/二进制内容带 BOM 会解析失败
 */
function downloadFile(content: BlobPart, filename: string, mimeType: string, withBom = false) {
  const part = withBom && typeof content === 'string' ? `\uFEFF${content}` : content
  const blob = new Blob([part], { type: mimeType })
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

export function useExport() {
  const { t } = useI18n()

  // 导出状态
  const exporting = ref(false)

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

      downloadFile(csv, filename, 'text/csv;charset=utf-8;', true)

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
   * 导出为 Excel（生成真实的 .xlsx 文件）
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

      // 确定列配置（未指定时使用首行所有字段）
      const firstItem = data[0] as object
      const cols: ExportColumn<T>[] = options.columns || Object.keys(firstItem).map(key => ({
        key,
        title: key,
      }))

      // 按需加载 exceljs，避免仅使用 CSV 导出的页面也加载该库
      const ExcelJS = (await import('exceljs')).default

      // 创建工作簿和工作表
      const workbook = new ExcelJS.Workbook()
      const worksheet = workbook.addWorksheet('Sheet1')

      // 设置表头列
      worksheet.columns = cols.map(col => ({
        header: col.title,
        key: col.key,
        width: 20,
      }))

      // 表头样式加粗
      worksheet.getRow(1).font = { bold: true }

      // 填充数据行
      data.forEach(record => {
        const recordObj = record as { [key: string]: unknown }
        const row: { [key: string]: unknown } = {}
        cols.forEach(col => {
          let value: unknown = recordObj[col.key]
          if (col.format) {
            value = col.format(value, record)
          }
          row[col.key] = value === null || value === undefined ? '' : value
        })
        worksheet.addRow(row)
      })

      // 生成 xlsx 二进制并触发下载
      const buffer = await workbook.xlsx.writeBuffer()
      const filename = `${options.filename || 'export'}.xlsx`

      downloadFile(buffer, filename, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')

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

  return {
    // 状态
    exporting,

    // 方法
    exportToCSV,
    exportToExcel,
  }
}
