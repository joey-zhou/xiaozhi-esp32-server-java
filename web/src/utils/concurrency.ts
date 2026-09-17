/**
 * 按并发上限逐个处理，返回值与入参一一对应
 * 任一项抛错时整体抛出，已在途的其余项不再取用结果
 */
export async function mapWithLimit<T, R>(
  items: T[],
  limit: number,
  handle: (item: T, index: number) => Promise<R>,
): Promise<R[]> {
  // 所有下标都由 runners 恰好写一次，最终无空洞
  const results: R[] = []
  let cursor = 0

  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (cursor < items.length) {
      const index = cursor++
      results[index] = await handle(items[index]!, index)
    }
  })

  await Promise.all(runners)
  return results
}
