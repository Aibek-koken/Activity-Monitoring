import { describe, expect, it } from 'vitest'
import { latestAllowedRecordDateString, todayLocalDateString } from './date'

describe('record date helpers', () => {
  it('formats the browser local calendar date for the default form date', () => {
    expect(todayLocalDateString(new Date(2026, 0, 2, 3, 4, 5))).toBe('2026-01-02')
  })

  it('uses UTC+14 as the latest allowed record date', () => {
    expect(latestAllowedRecordDateString(new Date(Date.UTC(2026, 0, 1, 11, 0, 0)))).toBe('2026-01-02')
  })
})
