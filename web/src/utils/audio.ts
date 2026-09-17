/**
 * 音频处理工具：WAV 编码相关
 *
 * 注意：采样率必须使用录音时 AudioContext 的实际采样率
 * （audioContext.sampleRate），浏览器可能不遵循请求的采样率，
 * 若写入请求值会导致播放变速。
 */

/**
 * 写入字符串到 DataView
 */
function writeString(view: DataView, offset: number, string: string): void {
  for (let i = 0; i < string.length; i++) {
    view.setUint8(offset + i, string.charCodeAt(i))
  }
}

/**
 * 浮点数转 16 位 PCM
 */
function floatTo16BitPCM(output: DataView, offset: number, input: Float32Array): void {
  for (let i = 0; i < input.length; i++, offset += 2) {
    const s = Math.max(-1, Math.min(1, input[i] || 0))
    output.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true)
  }
}

/**
 * 将单声道 Float32 采样数据编码为 16 位 PCM WAV
 *
 * @param samples 采样数据（范围 -1 ~ 1）
 * @param sampleRate 采样率，必须为录音时的实际采样率，否则播放会变速
 * @param numChannels 声道数
 */
export function encodeWAV(samples: Float32Array, sampleRate: number, numChannels: number): Blob {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)

  // RIFF 标识
  writeString(view, 0, 'RIFF')
  // RIFF 块大小
  view.setUint32(4, 36 + samples.length * 2, true)
  // WAVE 标识
  writeString(view, 8, 'WAVE')
  // fmt 子块标识
  writeString(view, 12, 'fmt ')
  // fmt 子块大小
  view.setUint32(16, 16, true)
  // 音频格式（1 表示 PCM）
  view.setUint16(20, 1, true)
  // 声道数
  view.setUint16(22, numChannels, true)
  // 采样率
  view.setUint32(24, sampleRate, true)
  // 字节率 = 采样率 * 每个样本的字节数 * 声道数
  view.setUint32(28, sampleRate * 2 * numChannels, true)
  // 块对齐 = 声道数 * 每个样本的字节数
  view.setUint16(32, numChannels * 2, true)
  // 每个样本的位数
  view.setUint16(34, 16, true)
  // data 子块标识
  writeString(view, 36, 'data')
  // data 子块大小
  view.setUint32(40, samples.length * 2, true)

  // 写入采样数据
  floatTo16BitPCM(view, 44, samples)

  return new Blob([view], { type: 'audio/wav' })
}
