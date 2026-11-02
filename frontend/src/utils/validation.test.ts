import { describe, expect, it } from 'vitest'
import { validateConfirmation, validateEmail, validateNewPassword } from './validation'

describe('validateEmail', () => {
  it('accepts a valid e-mail, ignoring surrounding spaces', () => {
    expect(validateEmail(' dona@exemplo.com ')).toBeUndefined()
  })

  it('rejects empty and malformed values', () => {
    expect(validateEmail('')).toBe('Informe o e-mail.')
    expect(validateEmail('dona@')).toBe('E-mail inválido.')
  })
})

describe('validateNewPassword', () => {
  it('requires 10 to 64 characters', () => {
    expect(validateNewPassword('curta')).toMatch(/pelo menos 10/)
    expect(validateNewPassword('uma frase boa')).toBeUndefined()
    expect(validateNewPassword('x'.repeat(65))).toMatch(/no máximo 64/)
  })

  it('counts emoji and accents as single characters', () => {
    expect(validateNewPassword('çãéíõ🎂🎂🎂🎂🎂')).toBeUndefined()
  })
})

describe('validateConfirmation', () => {
  it('requires the same value', () => {
    expect(validateConfirmation('abc', '')).toBe('Repita a nova senha.')
    expect(validateConfirmation('abc', 'abd')).toBe('As senhas não são iguais.')
    expect(validateConfirmation('abc', 'abc')).toBeUndefined()
  })
})
