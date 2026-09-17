import { describe, it, expect } from 'vitest'

import { fileValidators } from '../fileValidators'

// Helper: 创建指定大小与 MIME 的模拟文件（size 直接改写，避免真的分配几十 MB）
function createMockFile(name: string, size: number, type: string): File {
  const file = new File([], name, { type })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

describe('fileValidators.audio', () => {
  it('接受 wav/mp3/m4a/flac/ogg 五种扩展名', () => {
    for (const ext of ['wav', 'mp3', 'm4a', 'flac', 'ogg']) {
      expect(fileValidators.audio.validate(createMockFile(`sample.${ext}`, 1024, ''))).toBe(true)
    }
  })

  it('扩展名大小写不敏感', () => {
    expect(fileValidators.audio.validate(createMockFile('SAMPLE.M4A', 1024, ''))).toBe(true)
  })

  it('file.type 为空串时按扩展名判定', () => {
    expect(fileValidators.audio.validate(createMockFile('sample.m4a', 1024, ''))).toBe(true)
    expect(fileValidators.audio.validate(createMockFile('sample.txt', 1024, ''))).toBe('common.audioFormatError')
  })

  it('扩展名不在白名单但 MIME 是 audio/ 前缀时通过', () => {
    expect(fileValidators.audio.validate(createMockFile('sample.aac', 1024, 'audio/aac'))).toBe(true)
  })

  it('非音频返回 common.audioFormatError', () => {
    expect(fileValidators.audio.validate(createMockFile('sample.pdf', 1024, 'application/pdf')))
      .toBe('common.audioFormatError')
  })

  it('超过 10MB 返回 i18n key 而不是中文字面量', () => {
    const result = fileValidators.audio.validate(createMockFile('sample.wav', 10 * 1024 * 1024, 'audio/wav'))
    expect(result).toBe('common.audioSizeError')
  })

  it('刚好 10MB 视为超限，小于 10MB 通过', () => {
    expect(fileValidators.audio.validate(createMockFile('sample.wav', 10 * 1024 * 1024, 'audio/wav')))
      .toBe('common.audioSizeError')
    expect(fileValidators.audio.validate(createMockFile('sample.wav', 10 * 1024 * 1024 - 1, 'audio/wav')))
      .toBe(true)
  })
})

describe('fileValidators.image', () => {
  it('按 MIME 前缀判定图片', () => {
    expect(fileValidators.image.validate(createMockFile('a.png', 1024, 'image/png'))).toBe(true)
    expect(fileValidators.image.validate(createMockFile('a.png', 1024, ''))).toBe('common.onlyImageFiles')
  })

  it('超过 2MB 返回 common.imageSizeLimit', () => {
    expect(fileValidators.image.validate(createMockFile('a.png', 2 * 1024 * 1024, 'image/png')))
      .toBe('common.imageSizeLimit')
  })
})
