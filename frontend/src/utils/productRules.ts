/**
 * Regras do catálogo usadas pela cliente antes do envio (o servidor valida tudo de novo).
 */
import type { FulfillmentType, PublicProduct } from '../types/product'
import type { BakeryInfo } from '../types/public'
import { isoWeekday, WEEKDAY_NAMES } from './date'
import { formatBRL } from './money'

/** O produto aceita a data agendada (período e dias da semana). */
export function acceptsDate(product: PublicProduct, iso: string): boolean {
  if (product.availableFrom && iso < product.availableFrom) return false
  if (product.availableUntil && iso > product.availableUntil) return false
  return product.availableDays.length === 0 || product.availableDays.includes(isoWeekday(iso))
}

/** Formas de atendimento possíveis para o conjunto escolhido. */
export function allowedFulfillment(
  bakery: Pick<BakeryInfo, 'pickupEnabled' | 'deliveryEnabled' | 'customCakeDelivery'>,
  customCake: boolean,
  products: PublicProduct[],
): FulfillmentType[] {
  const result: FulfillmentType[] = []
  if (bakery.pickupEnabled && products.every((p) => p.pickupAllowed)) result.push('PICKUP')
  if (
    bakery.deliveryEnabled &&
    products.every((p) => p.deliveryAllowed) &&
    (!customCake || bakery.customCakeDelivery)
  )
    result.push('DELIVERY')
  return result
}

/** "R$ 3,50 / unidade" ou "Sob orçamento". */
export function priceLabel(price: string | null, unitLabel: string | null): string {
  if (price == null) return 'Sob orçamento'
  return unitLabel ? `${formatBRL(price)} / ${unitLabel}` : formatBRL(price)
}

/** Texto curto de disponibilidade: "Somente terça e quinta · até 24/12". */
export function availabilityLabel(product: Pick<PublicProduct, 'availableDays' | 'availableFrom' | 'availableUntil'>): string {
  const parts: string[] = []
  if (product.availableDays.length > 0 && product.availableDays.length < 7) {
    const names = product.availableDays.map((d) => WEEKDAY_NAMES[d].toLowerCase())
    parts.push(`Somente ${names.length === 1 ? names[0] : `${names.slice(0, -1).join(', ')} e ${names[names.length - 1]}`}`)
  }
  const short = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`
  if (product.availableFrom && product.availableUntil) parts.push(`de ${short(product.availableFrom)} a ${short(product.availableUntil)}`)
  else if (product.availableFrom) parts.push(`a partir de ${short(product.availableFrom)}`)
  else if (product.availableUntil) parts.push(`até ${short(product.availableUntil)}`)
  return parts.join(' · ')
}

export const FULFILLMENT_LABELS: Record<FulfillmentType, string> = {
  PICKUP: 'Retirada na confeitaria',
  DELIVERY: 'Delivery',
}
