import { http } from './request'
import api from './api'
import type { PromptTemplate, TemplateQuery } from '@/types/template'
import type { ApiResponse, PageData } from '@/types/api'

export function queryTemplates(params: TemplateQuery): Promise<ApiResponse<PageData<PromptTemplate>>> {
  return http.get(api.template.query, params)
}

export function addTemplate(data: Partial<PromptTemplate>): Promise<ApiResponse<void>> {
  return http.post(api.template.add, data)
}

export function updateTemplate(data: Partial<PromptTemplate>): Promise<ApiResponse<void>> {
  return http.post(api.template.update, data)
}

export function deleteTemplate(templateId: number): Promise<ApiResponse<void>> {
  return http.post(api.template.update, {
    templateId,
    state: 0
  })
}

export function setDefaultTemplate(templateId: number): Promise<ApiResponse<void>> {
  return http.post(api.template.update, {
    templateId,
    isDefault: 1
  })
}

