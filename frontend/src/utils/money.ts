/**
 * Valores monetários trafegam como string decimal ("120.50") — nunca como number —
 * para evitar erros de arredondamento (ADR-008). Aqui só formatamos e interpretamos.
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
