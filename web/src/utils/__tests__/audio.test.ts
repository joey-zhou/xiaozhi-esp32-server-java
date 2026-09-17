import { describe, expect, it } from 'vitest'

import { encodeWAV } from '../audio'

async function headerOf(blob: Blob) {
  return new DataView(await blob.arrayBuffer())
}

function ascii(view: DataView, offset: number, length: number) {
  let out = ''
  for (let i = 0; i < length; i++) out += String.fromCharCode(view.getUint8(offset + i))
  return out
}

describe('encodeWAV', () => {
  it('写出标准的 44 字节 RIFF/WAVE 头', async () => {
    const samples = new Float32Array([0, 0.5, -0.5, 1])
    const view = await headerOf(encodeWAV(samples, 16000, 1))

    expect(view.byteLength).toBe(44 + samples.length * 2)
    expect(ascii(view, 0, 4)).toBe('RIFF')
    expect(ascii(view, 8, 4)).toBe('WAVE')
    expect(ascii(view, 12, 4)).toBe('fmt ')
    expect(ascii(view, 36, 4)).toBe('data')
    expect(view.getUint32(4, true)).toBe(36 + samples.length * 2)
    expect(view.getUint32(16, true)).toBe(16)
    expect(view.getUint16(20, true)).toBe(1)
    expect(view.getUint32(40, true)).toBe(samples.length * 2)
  })

  // 采样率写错会让播放变速，这里锁死「写进头里的就是传进来的实际采样率」
  it('采样率、声道数、字节率、块对齐按入参写入', async () => {
    const view = await headerOf(encodeWAV(new Float32Array(8), 44100, 2))

    expect(view.getUint16(22, true)).toBe(2)
    expect(view.getUint32(24, true)).toBe(44100)
    expect(view.getUint32(28, true)).toBe(44100 * 2 * 2)
    expect(view.getUint16(32, true)).toBe(2 * 2)
    expect(view.getUint16(34, true)).toBe(16)
  })

  it('浮点采样按正负分别用 0x8000 / 0x7fff 缩放，并裁剪到 [-1, 1]', async () => {
    const view = await headerOf(encodeWAV(new Float32Array([0, 1, -1, 2, -2]), 16000, 1))

    expect(view.getInt16(44, true)).toBe(0)
    expect(view.getInt16(46, true)).toBe(32767)
    expect(view.getInt16(48, true)).toBe(-32768)
    // 超出范围的样本被夹到边界，不会溢出成反相
    expect(view.getInt16(50, true)).toBe(32767)
    expect(view.getInt16(52, true)).toBe(-32768)
  })
})
