import { http } from './request'
import api from './api'
import type { PromptTemplate, TemplateQuery } from '@/types/template'

export function queryTemplates(params: Partial<TemplateQuery>) {
  return http.getPage<PromptTemplate>(api.template, params)
}

export function addTemplate(data: Partial<PromptTemplate>) {
  return http.post(api.template, data)
}

export function updateTemplate(data: Partial<PromptTemplate>) {
  return http.put(`${api.template}/${data.templateId}`, data)
}

export function deleteTemplate(templateId: number) {
  return http.delete(`${api.template}/${templateId}`)
}

export function setDefaultTemplate(templateId: number) {
  return http.put(`${api.template}/${templateId}`, {
    isDefault: 1
  })
}

