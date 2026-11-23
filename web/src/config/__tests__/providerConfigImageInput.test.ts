import { describe, expect, it } from 'vitest'
import { acceptsImageInput } from '../providerConfig'

describe('acceptsImageInput', () => {
  it('视觉类型的配置能看图', () => {
    expect(acceptsImageInput('vision', undefined)).toBe(true)
  })

  it('对话模型按清单的 IMAGE2TEXT 标记判断', () => {
    expect(acceptsImageInput('chat', { llm_name: 'gpt-4o', model_type: 'chat', tags: 'LLM,CHAT,128K,IMAGE2TEXT' })).toBe(true)
    expect(acceptsImageInput('chat', { llm_name: 'deepseek-chat', model_type: 'chat', tags: 'LLM,CHAT,64K' })).toBe(false)
  })

  it('清单里没有的模型当作不能看图', () => {
    expect(acceptsImageInput('chat', undefined)).toBe(false)
  })
})
