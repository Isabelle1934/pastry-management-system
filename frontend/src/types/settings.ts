import type { Money } from './catalog'

export interface BakerySettings {
  name: string | null
  aboutText: string | null
  whatsappContact: string | null
  instagram: string | null
  address: string | null
  pickupInstructions: string | null
  pixKey: string | null
  pixHolderName: string | null
  quoteValidityHours: number
  quoteExpiryWarningHours: number
  depositDeadlineHours: number | null
  ownerNotificationEmail: string | null
  pickupEnabled: boolean
  deliveryEnabled: boolean
  deliveryInfo: string | null
  customCakeEnabled: boolean
  customCakeDelivery: boolean
  /** Taxa de entrega dentro da cidade. */
  deliveryFee: Money
  /** Link de pagamento no cartão de crédito (sem integração automática). */
  cardPaymentLink: string | null
  /** O sinal precisa ser pago até estas horas antes da data agendada; 0 = sem limite. */
  depositCutoffHours: number
  /** Delivery de outros doces dentro da cidade. */
  deliveryFeeSweets: Money
  /** Entrega no local da festa. */
  partyVenueFee: Money
  /** Só com a confirmação a taxa do local da festa entra sozinha no orçamento. */
  partyVenueFeeConfirmed: boolean
  punctualityNotice: string
  /** Lembrete de pagamento: horas antes do fim do prazo do sinal. */
  depositReminderHours: number
  version: number
}

export type BakerySettingsInput = Omit<BakerySettings, 'version'> & { version: number }
