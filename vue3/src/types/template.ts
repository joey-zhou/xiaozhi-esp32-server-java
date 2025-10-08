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

export interface TemplateQuery {
  templateName?: string
  category?: string
  start?: number
  limit?: number
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

