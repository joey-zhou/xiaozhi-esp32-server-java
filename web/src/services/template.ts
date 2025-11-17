import { http } from './request'
import api from './api'
import type { PromptTemplate, TemplateQuery } from '@/types/template'

export function queryTemplates(params: Partial<TemplateQuery>) {
  return http.getPage<PromptTemplate>(api.template.query, params)
}

export function addTemplate(data: Partial<PromptTemplate>) {
  return http.post(api.template.add, data)
}

export function updateTemplate(data: Partial<PromptTemplate>) {
  return http.post(api.template.update, data)
}

export function deleteTemplate(templateId: number) {
  return http.post(api.template.update, {
    templateId,
    state: '0'
  })
}

export function setDefaultTemplate(templateId: number) {
  return http.post(api.template.update, {
    templateId,
    isDefault: 1
  })
}

