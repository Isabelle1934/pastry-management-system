import type { QuoteBreakdown, QuoteItemKind } from '../types/order'
import { fromCents } from './money'

export const QUOTE_ITEM_KINDS: QuoteItemKind[] = ['PRODUCT', 'EXTRA', 'TOPPER', 'DELIVERY']

export const QUOTE_KIND_LABELS: Record<QuoteItemKind, string> = {
  PRODUCT: 'Produto',
  EXTRA: 'Adicional',
  TOPPER: 'Topo de bolo',
  DELIVERY: 'Taxa de entrega',
}

/** Linhas do resumo do orçamento, na ordem em que aparecem. */
export const BREAKDOWN_ROWS: { key: keyof QuoteBreakdown; label: string }[] = [
  { key: 'products', label: 'Produtos' },
  { key: 'extras', label: 'Adicionais' },
  { key: 'topper', label: 'Topo de bolo' },
  { key: 'delivery', label: 'Taxa de entrega' },
]

const KIND_TO_KEY: Record<QuoteItemKind, keyof QuoteBreakdown> = {
  PRODUCT: 'products',
  EXTRA: 'extras',
  TOPPER: 'topper',
  DELIVERY: 'delivery',
}

/** Soma os subtotais (em centavos) por grupo — mesma regra do servidor. */
export function breakdownOf(lines: { kind: QuoteItemKind; cents: number }[]): QuoteBreakdown {
  const cents: Record<keyof QuoteBreakdown, number> = { products: 0, extras: 0, topper: 0, delivery: 0 }
  for (const line of lines) cents[KIND_TO_KEY[line.kind]] += line.cents
  return {
    products: fromCents(cents.products),
    extras: fromCents(cents.extras),
    topper: fromCents(cents.topper),
    delivery: fromCents(cents.delivery),
  }
}
