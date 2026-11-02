import { describe, expect, it } from 'vitest'
import { currentMonth, formatMonth, isMonthKey, monthRange, shiftMonth } from './period'

describe('month helpers', () => {
  it('computes the first and last day of the month, including leap years', () => {
    expect(monthRange('2026-11')).toEqual({ from: '2026-11-01', to: '2026-11-30' })
    expect(monthRange('2028-02')).toEqual({ from: '2028-02-01', to: '2028-02-29' })
    expect(monthRange('2026-02')).toEqual({ from: '2026-02-01', to: '2026-02-28' })
  })

  it('moves across year boundaries', () => {
    expect(shiftMonth('2026-12', 1)).toBe('2027-01')
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
    expect(shiftMonth('2026-11', 0)).toBe('2026-11')
  })

  it('uses the bakery timezone for the current month', () => {
    // 01/12 01:00 UTC ainda é 30/11 em São Paulo.
    expect(currentMonth(new Date('2026-12-01T01:00:00Z'))).toBe('2026-11')
    expect(currentMonth(new Date('2026-12-01T04:00:00Z'))).toBe('2026-12')
  })

  it('validates and formats month keys', () => {
    expect(isMonthKey('2026-11')).toBe(true)
    expect(isMonthKey('2026-13')).toBe(false)
    expect(isMonthKey(null)).toBe(false)
    expect(formatMonth('2026-03')).toBe('Março de 2026')
  })
})
