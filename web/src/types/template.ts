export interface PromptTemplate {
  templateId?: number
  templateName: string
  category: string
  templateDesc?: string
  templateContent: string
  isDefault: number | string
  state?: number | string
  createTime?: string
  updateTime?: string
}

import type { BaseQueryParams } from './api'

export interface TemplateQuery extends BaseQueryParams {
  templateName?: string
  category?: string
}

export interface TemplateFormData {
  templateName: string
  category: string
  customCategory?: string
  templateDesc?: string
  templateContent: string
  isDefault: boolean
}

export interface CategoryOption {
  label: string
  value: string
}
