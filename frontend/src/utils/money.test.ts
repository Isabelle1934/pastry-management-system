import { describe, expect, it } from 'vitest'
import { formatBRL, parseMoneyInput, toMoneyInput } from './money'

describe('formatBRL', () => {
  it.each([
    ['120.5', 'R$ 120,50'],
    ['0.00', 'R$ 0,00'],
    ['1234.56', 'R$ 1.234,56'],
    ['99999.99', 'R$ 99.999,99'],
    ['165', 'R$ 165,00'],
  ])('%s → %s', (input, expected) => {
    expect(formatBRL(input)).toBe(expected)
  })

  it('returns empty text for missing values', () => {
    expect(formatBRL(null)).toBe('')
    expect(formatBRL('')).toBe('')
  })
})

describe('toMoneyInput', () => {
  it('converts the API format to the form format', () => {
    expect(toMoneyInput('120.50')).toBe('120,50')
    expect(toMoneyInput('7')).toBe('7,00')
  })
})

describe('parseMoneyInput', () => {
  it.each([
    ['120', '120.00'],
    ['120,5', '120.50'],
    ['120,50', '120.50'],
    ['1.234,56', '1234.56'],
    ['R$ 99,90', '99.90'],
    ['0,99', '0.99'],
    ['0012', '12.00'],
  ])('%s → %s', (input, expected) => {
    expect(parseMoneyInput(input)).toBe(expected)
  })

  it.each(['', 'abc', '12,345', '-5', '1,2,3', '12.34', '1.23,00'])('rejects %s', (input) => {
    expect(parseMoneyInput(input)).toBeNull()
  })
})
