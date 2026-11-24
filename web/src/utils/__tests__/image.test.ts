import { describe, expect, it } from 'vitest'
import { fitSize } from '../image'

describe('fitSize', () => {
  it('最长边超出时等比缩到上限', () => {
    expect(fitSize(4000, 3000, 2048)).toEqual({ width: 2048, height: 1536 })
    expect(fitSize(1000, 5000, 2048)).toEqual({ width: 410, height: 2048 })
  })

  it('已经够小的原样返回', () => {
    expect(fitSize(800, 600, 2048)).toEqual({ width: 800, height: 600 })
  })
})
