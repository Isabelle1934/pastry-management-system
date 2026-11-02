import { describe, expect, it } from 'vitest'
import { addDays, formatDate, formatLongDay, formatShortDay, isoWeekday, todayIso } from './date'

describe('date utils', () => {
  it('uses the bakery time zone for "today", even near midnight UTC', () => {
    // 02:30 UTC de terça = 23:30 de segunda em São Paulo.
    expect(todayIso(new Date('2026-11-03T02:30:00Z'))).toBe('2026-11-02')
    expect(todayIso(new Date('2026-11-03T03:30:00Z'))).toBe('2026-11-03')
  })

  it('adds days across months and years', () => {
    expect(addDays('2026-11-30', 1)).toBe('2026-12-01')
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28')
  })

  it('computes ISO weekdays (1 = Monday, 7 = Sunday)', () => {
    expect(isoWeekday('2026-11-02')).toBe(1)
    expect(isoWeekday('2026-11-08')).toBe(7)
  })

  it('formats dates without time zone shifts', () => {
    expect(formatDate('2026-11-03')).toBe('03/11/2026')
    expect(formatShortDay('2026-11-03')).toBe('Ter, 03/11')
    expect(formatLongDay('2026-11-08')).toBe('Domingo, 08/11/2026')
  })
})
