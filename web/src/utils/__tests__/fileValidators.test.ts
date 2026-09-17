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

  it('MIME 不在白名单但扩展名在白名单时通过（走扩展名兜底）', () => {
    expect(fileValidators.audio.validate(createMockFile('sample.aac', 1024, 'audio/aac'))).toBe(true)
  })

  it('MIME 带 audio/ 前缀但既不在白名单也不是受支持扩展名时拒绝', () => {
    // 收敛前是按 MIME 前缀放行的，收窄后要求 MIME 白名单或扩展名二选一命中，
    // 与后端 FileUploadController 的允许列表对齐
    expect(fileValidators.audio.validate(createMockFile('sample.weba', 1024, 'audio/webm')))
      .toBe('common.audioFormatError')
  })

  it('无扩展名且 MIME 不在白名单时拒绝', () => {
    expect(fileValidators.audio.validate(createMockFile('sample', 1024, ''))).toBe('common.audioFormatError')
  })

  it('无扩展名但 MIME 命中白名单时通过', () => {
    expect(fileValidators.audio.validate(createMockFile('sample', 1024, 'audio/mp3'))).toBe(true)
  })

  it('空文件（0 字节）不会被误判为超限', () => {
    expect(fileValidators.audio.validate(createMockFile('sample.mp3', 0, 'audio/mp3'))).toBe(true)
  })

  it('非音频返回 common.audioFormatError', () => {
    expect(fileValidators.audio.validate(createMockFile('sample.pdf', 1024, 'application/pdf')))
      .toBe('common.audioFormatError')
  })

  it('超过 10MB 返回 i18n key 而不是中文字面量', () => {
    const result = fileValidators.audio.validate(createMockFile('sample.wav', 10 * 1024 * 1024, 'audio/wav'))
    expect(result).toBe('common.audioSizeError')
  })

  it('刚好 10MB 视为超限，超限 1 字节也拒绝，小于 10MB 通过', () => {
    expect(fileValidators.audio.validate(createMockFile('sample.wav', 10 * 1024 * 1024, 'audio/wav')))
      .toBe('common.audioSizeError')
    expect(fileValidators.audio.validate(createMockFile('sample.wav', 10 * 1024 * 1024 + 1, 'audio/wav')))
      .toBe('common.audioSizeError')
    expect(fileValidators.audio.validate(createMockFile('sample.wav', 10 * 1024 * 1024 - 1, 'audio/wav')))
      .toBe(true)
  })
})

describe('fileValidators.image', () => {
  it('MIME 命中白名单时通过', () => {
    expect(fileValidators.image.validate(createMockFile('a.png', 1024, 'image/png'))).toBe(true)
  })

  it('MIME 缺失但扩展名命中白名单时通过（浏览器部分来源不给标准 MIME）', () => {
    expect(fileValidators.image.validate(createMockFile('a.png', 1024, ''))).toBe(true)
    expect(fileValidators.image.validate(createMockFile('a.webp', 1024, ''))).toBe(true)
  })

  it('MIME 带 image/ 前缀但既不在白名单也不是受支持扩展名时拒绝', () => {
    // 收敛前是按 MIME 前缀放行的，SVG 之类的矢量图会在本站源里执行内嵌脚本，收窄后一并挡掉
    expect(fileValidators.image.validate(createMockFile('logo.svg', 1024, 'image/svg+xml')))
      .toBe('common.onlyImageFiles')
  })

  it('无扩展名且 MIME 不在白名单时拒绝', () => {
    expect(fileValidators.image.validate(createMockFile('avatar', 1024, ''))).toBe('common.onlyImageFiles')
  })

  it('非图片返回 common.onlyImageFiles', () => {
    expect(fileValidators.image.validate(createMockFile('a.pdf', 1024, 'application/pdf')))
      .toBe('common.onlyImageFiles')
  })

  it('空文件（0 字节）不会被误判为超限', () => {
    expect(fileValidators.image.validate(createMockFile('a.png', 0, 'image/png'))).toBe(true)
  })

  it('刚好 2MB 视为超限，超限 1 字节也拒绝，小于 2MB 通过', () => {
    expect(fileValidators.image.validate(createMockFile('a.png', 2 * 1024 * 1024, 'image/png')))
      .toBe('common.imageSizeLimit')
    expect(fileValidators.image.validate(createMockFile('a.png', 2 * 1024 * 1024 + 1, 'image/png')))
      .toBe('common.imageSizeLimit')
    expect(fileValidators.image.validate(createMockFile('a.png', 2 * 1024 * 1024 - 1, 'image/png')))
      .toBe(true)
  })
})
