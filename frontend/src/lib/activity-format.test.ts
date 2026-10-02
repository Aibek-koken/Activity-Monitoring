import { describe, expect, it } from 'vitest'
import { formatDate, formatVolume, formatWorkHours, toNumberOrNull } from './activity-format'

describe('toNumberOrNull', () => {
  it('passes numbers through', () => {
    expect(toNumberOrNull(2.5)).toBe(2.5)
    expect(toNumberOrNull(0)).toBe(0)
  })

  it('parses decimal strings from BigDecimal-style payloads', () => {
    expect(toNumberOrNull('8.25')).toBe(8.25)
  })

  it('treats absent and empty values as null', () => {
    expect(toNumberOrNull(null)).toBeNull()
    expect(toNumberOrNull(undefined)).toBeNull()
    expect(toNumberOrNull('')).toBeNull()
    expect(toNumberOrNull('not a number')).toBeNull()
  })
})

describe('formatVolume', () => {
  it('drops trailing zeros for whole numbers', () => {
    expect(formatVolume(20)).toBe('20')
  })

  it('keeps two decimals for fractional volumes', () => {
    expect(formatVolume(8.25)).toBe('8.25')
    expect(formatVolume(12.5)).toBe('12.50')
  })

  it('groups thousands with the en-US locale regardless of browser locale', () => {
    expect(formatVolume(1234.5)).toBe('1,234.50')
  })

  it('falls back to zero for absent values', () => {
    expect(formatVolume(undefined)).toBe('0')
    expect(formatVolume(null)).toBe('0')
  })
})

describe('formatWorkHours', () => {
  it('renders a placeholder when work hours are missing or null', () => {
    expect(formatWorkHours(undefined)).toBe('Not entered')
    expect(formatWorkHours(null)).toBe('Not entered')
  })

  it('renders decimal hours as hours and minutes', () => {
    expect(formatWorkHours(3)).toBe('3 h')
    expect(formatWorkHours(2.5)).toBe('2 h 30 min')
    expect(formatWorkHours('4')).toBe('4 h')
    expect(formatWorkHours(0.25)).toBe('15 min')
    expect(formatWorkHours(0)).toBe('0 h')
  })
})

describe('formatDate', () => {
  it('formats ISO dates in en-US regardless of browser locale', () => {
    expect(formatDate('2026-09-30')).toBe('Sep 30, 2026')
    expect(formatDate('2026-12-01')).toBe('Dec 1, 2026')
  })

  it('handles absent and unparsable values', () => {
    expect(formatDate(null)).toBe('No records yet')
    expect(formatDate(undefined)).toBe('No records yet')
    expect(formatDate('not-a-date')).toBe('No records yet')
  })
})
