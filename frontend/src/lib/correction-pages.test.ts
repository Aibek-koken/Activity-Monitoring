import { describe, expect, it } from 'vitest'
import { parseCorrectionPages } from './correction-pages'

describe('parseCorrectionPages', () => {
  it('expands ranges and removes repeated page numbers', () => {
    expect(parseCorrectionPages('1, 3–5, 4, 9-10')).toEqual([1, 3, 4, 5, 9, 10])
  })

  it('rejects empty, reversed, zero and malformed ranges', () => {
    for (const input of ['', '0', '5-3', '1,,2', '1-2-3', '10001']) {
      expect(parseCorrectionPages(input)).toBeNull()
    }
  })
})
