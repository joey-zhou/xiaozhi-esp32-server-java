/** 等比缩到最长边不超过 maxEdge，已经够小的原样返回 */
export function fitSize(width: number, height: number, maxEdge: number): { width: number; height: number } {
  const longest = Math.max(width, height)
  if (longest <= maxEdge) return { width, height }
  const scale = maxEdge / longest
  return { width: Math.round(width * scale), height: Math.round(height * scale) }
}

/**
 * 把浏览器能显示的图转成 JPEG 并缩到 maxEdge 内；本来就是不超限的 JPEG/PNG 原样返回
 *
 * @throws 浏览器解不开的文件
 */
export async function fitImage(file: File, maxEdge: number, maxBytes: number): Promise<File> {
  const bitmap = await createImageBitmap(file)
  try {
    const size = fitSize(bitmap.width, bitmap.height, maxEdge)
    const unchanged = size.width === bitmap.width && size.height === bitmap.height
    if (unchanged && file.size <= maxBytes && (file.type === 'image/jpeg' || file.type === 'image/png')) {
      return file
    }
    const canvas = document.createElement('canvas')
    canvas.width = size.width
    canvas.height = size.height
    canvas.getContext('2d')?.drawImage(bitmap, 0, 0, size.width, size.height)
    const blob = await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('image encode failed'))), 'image/jpeg', 0.85)
    )
    return new File([blob], file.name.replace(/\.[^.]*$/, '') + '.jpg', { type: 'image/jpeg' })
  } finally {
    bitmap.close()
  }
}
