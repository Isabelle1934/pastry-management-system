import { describe, expect, it } from 'vitest'
import { depositOf, formatBRL, lineTotal, parseMoneyInput, toMoneyInput } from './money'

describe('quote math (same rules as the server)', () => {
  it('multiplies quantity by unit price rounding half up to cents', () => {
    expect(lineTotal('1.00', '120.00')).toBe('120.00')
    expect(lineTotal('3.00', '55.19')).toBe('165.57')
    expect(lineTotal('0.50', '0.03')).toBe('0.02')
    expect(lineTotal('2.50', '10.01')).toBe('25.03')
  })

  it('splits the deposit as half rounded up and the balance as the rest', () => {
    expect(depositOf('165.00')).toEqual({ deposit: '82.50', balance: '82.50' })
    expect(depositOf('165.59')).toEqual({ deposit: '82.80', balance: '82.79' })
    expect(depositOf('0.01')).toEqual({ deposit: '0.01', balance: '0.00' })
  })
})

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
