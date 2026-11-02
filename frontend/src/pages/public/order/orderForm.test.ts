import { describe, expect, it } from 'vitest'
import type { PublicSize } from '../../../types/public'
import {
  applySize,
  initialOrderForm,
  looksLikeBrazilianPhone,
  toggleChoice,
  toPayload,
  validatePhoto,
  validateStep,
  type OrderFormState,
} from './orderForm'

const size: PublicSize = {
  id: 7,
  name: 'Médio',
  description: null,
  servings: 20,
  basePrice: '150.00',
  maxFlavors: 2,
  maxFillings: 1,
}

const file = (name: string, type: string, bytes = 100) => new File([new Uint8Array(bytes)], name, { type })

describe('looksLikeBrazilianPhone', () => {
  it.each(['(11) 99999-8888', '+55 11 99999 8888', '011999998888', '(21) 3456-7890'])('accepts %s', (value) => {
    expect(looksLikeBrazilianPhone(value)).toBe(true)
  })

  it.each(['', '9999-8888', '(11) 89999-8888', '(01) 99999-8888', '(11) 1234-5678'])('rejects %s', (value) => {
    expect(looksLikeBrazilianPhone(value)).toBe(false)
  })
})

describe('validateStep', () => {
  it('requires name and phone on step 1', () => {
    expect(validateStep(0, initialOrderForm, undefined)).toEqual({
      customerName: 'Informe seu nome completo.',
      customerWhatsapp: 'Informe seu WhatsApp.',
    })
    expect(
      validateStep(0, { ...initialOrderForm, customerName: 'Ana', customerWhatsapp: '11999998888' }, undefined),
    ).toEqual({})
  })

  it('requires date then time on step 2', () => {
    expect(validateStep(1, initialOrderForm, undefined)).toHaveProperty('pickupDate')
    expect(validateStep(1, { ...initialOrderForm, pickupDate: '2026-11-03' }, undefined)).toHaveProperty('pickupTime')
  })

  it('enforces the size limits on step 3', () => {
    const form: OrderFormState = { ...initialOrderForm, sizeId: 7, flavorIds: [1, 2, 3], fillingIds: [] }
    const errors = validateStep(2, form, size)
    expect(errors.flavorIds).toBe('Este tamanho permite até 2 massas.')
    expect(errors.fillingIds).toBe('Escolha pelo menos um recheio.')
  })

  it('requires the theme only when a topper is requested', () => {
    expect(validateStep(3, initialOrderForm, size)).toEqual({})
    expect(validateStep(3, { ...initialOrderForm, topperRequested: true, topperAge: 'dez' }, size)).toEqual({
      topperTheme: 'Informe o tema do topo.',
      topperAge: 'Informe a idade em números.',
    })
  })
})

describe('validatePhoto', () => {
  it('accepts images up to 5 MB', () => {
    expect(validatePhoto(file('bolo.jpg', 'image/jpeg'))).toBeUndefined()
    expect(validatePhoto(file('bolo.webp', 'image/webp'))).toBeUndefined()
  })

  it('rejects other types and large files', () => {
    expect(validatePhoto(file('doc.pdf', 'application/pdf'))).toMatch(/não é uma foto aceita/)
    expect(validatePhoto(file('bolo.jpg', 'image/jpeg', 5 * 1024 * 1024 + 1))).toMatch(/mais de 5 MB/)
  })
})

describe('choices', () => {
  it('replaces the choice when only one is allowed and caps at the limit otherwise', () => {
    expect(toggleChoice([1], 2, 1)).toEqual([2])
    expect(toggleChoice([1, 2], 3, 2)).toEqual([1, 2])
    expect(toggleChoice([1, 2], 2, 2)).toEqual([1])
  })

  it('trims choices that no longer fit when the size changes', () => {
    const form = applySize({ ...initialOrderForm, flavorIds: [1, 2, 3], fillingIds: [4, 5] }, size)
    expect(form.flavorIds).toEqual([1, 2])
    expect(form.fillingIds).toEqual([4])
    expect(form.sizeId).toBe(7)
  })
})

describe('toPayload', () => {
  it('drops topper fields when no topper is requested and converts the age', () => {
    expect(toPayload({ ...initialOrderForm, topperTheme: 'Jardim' }).topperTheme).toBe('')
    expect(toPayload({ ...initialOrderForm, topperRequested: true, topperTheme: ' Jardim ', topperAge: '5' })).toMatchObject({
      topperTheme: 'Jardim',
      topperAge: 5,
    })
  })
})
