/**
 * Valores monetários trafegam como string decimal ("120.50") — nunca como number —
 * para evitar erros de arredondamento (ADR-008). Aqui só formatamos, interpretamos e
 * fazemos contas em centavos inteiros para pré-visualização (o servidor é quem calcula de fato).
 */

const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

/** "120.5" → "R$ 120,50" */
export function formatBRL(value: string | null | undefined): string {
  if (value == null || value === '') return ''
  const [integer, fraction = ''] = value.split('.')
  // Formata a parte inteira com Intl e monta os centavos sem passar por ponto flutuante.
  const cents = (fraction + '00').slice(0, 2)
  const negative = integer.startsWith('-')
  const formattedInteger = BRL.format(Math.abs(Number(integer))).replace(/,00$/, '')
  return `${negative ? '-' : ''}${formattedInteger},${cents}`.replace(/ /g, ' ')
}

/** "120.50" (API) → "120,50" (campo de formulário) */
export function toMoneyInput(value: string | null | undefined): string {
  if (value == null || value === '') return ''
  const [integer, fraction = ''] = value.split('.')
  return `${integer},${(fraction + '00').slice(0, 2)}`
}

/**
 * Interpreta o que a usuária digitou ("1.234,5", "120", "R$ 99,90") e devolve
 * a string decimal da API ("1234.50"), ou null se não for um valor válido.
 */
export function parseMoneyInput(input: string): string | null {
  const cleaned = input.replace(/R\$|\s/g, '')
  if (!/^\d{1,3}(\.\d{3})*(,\d{1,2})?$|^\d+(,\d{1,2})?$/.test(cleaned)) return null
  const [integer, fraction = ''] = cleaned.replace(/\./g, '').split(',')
  return `${String(Number(integer))}.${(fraction + '00').slice(0, 2)}`
}

/** "120.50" → 12050 */
export function toCents(value: string): number {
  const [integer, fraction = ''] = value.split('.')
  return Number(integer) * 100 + Number((fraction + '00').slice(0, 2))
}

/** 12050 → "120.50" */
export function fromCents(cents: number): string {
  const integer = Math.floor(cents / 100)
  return `${integer}.${String(cents % 100).padStart(2, '0')}`
}

/** Quantidade × valor unitário, arredondado para centavos (meio para cima), como no servidor. */
export function lineTotal(quantity: string, unitPrice: string): string {
  const quantityHundredths = toCents(quantity)
  const product = quantityHundredths * toCents(unitPrice)
  return fromCents(Math.floor((product + 50) / 100))
}

/** Sinal de 50% arredondado em centavos (meio para cima); saldo = total − sinal. */
export function depositOf(total: string): { deposit: string; balance: string } {
  const totalCents = toCents(total)
  const deposit = Math.floor((totalCents + 1) / 2)
  return { deposit: fromCents(deposit), balance: fromCents(totalCents - deposit) }
}
