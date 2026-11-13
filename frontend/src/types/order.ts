import type { Money } from './catalog'
import type { FulfillmentType, OrderItemView, DeliveryZone } from './product'

export type CommercialStatus =
  | 'PENDING_REVIEW'
  | 'QUOTE_SENT'
  | 'AWAITING_DEPOSIT'
  | 'CONFIRMED'
  | 'COMPLETED'
  | 'REJECTED'
  | 'DECLINED_BY_CLIENT'
  | 'CANCELLED'

export type PaymentStatus = 'NONE' | 'DEPOSIT_UNDER_REVIEW' | 'DEPOSIT_PAID' | 'PAID'
export type ProductionStatus = 'NOT_STARTED' | 'IN_PRODUCTION' | 'READY' | 'DELIVERED'
export type QuoteStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED' | 'SUPERSEDED'
export type Actor = 'OWNER' | 'CLIENT' | 'SYSTEM'

export interface Choice {
  id: number
  name: string
}

/** Grupo do item no orçamento: produtos, adicionais, topo de bolo ou entrega. */
export type QuoteItemKind = 'PRODUCT' | 'EXTRA' | 'TOPPER' | 'DELIVERY'

/** Subtotais por grupo (a soma é o total do orçamento). */
export interface QuoteBreakdown {
  products: Money
  extras: Money
  topper: Money
  delivery: Money
}

export interface QuoteItem {
  kind: QuoteItemKind
  name: string
  description: string | null
  quantity: Money
  unitPrice: Money
  subtotal: Money
}

export interface Quote {
  id: number
  versionNumber: number
  status: QuoteStatus
  items: QuoteItem[]
  breakdown: QuoteBreakdown
  total: Money
  depositAmount: Money
  balanceAmount: Money
  notes: string | null
  validityHours: number
  validUntil: string | null
  sentAt: string | null
  respondedAt: string | null
  declineReason: string | null
  expiredAt: string | null
  createdAt: string
}

export interface OrderSummary {
  id: number
  trackingCode: string
  customerName: string
  customerWhatsapp: string
  pickupDate: string
  pickupTime: string
  /** Vazio = pedido sem bolo personalizado. */
  sizeName: string | null
  /** Resumo de bolo e itens, ex.: "Bolo Médio + Brigadeiro ×50". */
  summary: string
  fulfillmentType: FulfillmentType
  commercialStatus: CommercialStatus
  paymentStatus: PaymentStatus
  productionStatus: ProductionStatus
  quoteTotal: Money | null
  createdAt: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface FileInfo {
  id: number
  purpose: 'ORDER_REFERENCE' | 'TOPPER_REFERENCE' | 'PAYMENT_PROOF' | 'PRODUCT_PHOTO'
  originalName: string
  contentType: string
  sizeBytes: number
  uploadedBy: Actor
  createdAt: string
}

export interface HistoryEntry {
  eventType: string
  actor: Actor
  description: string
  details: Record<string, unknown> | null
  createdAt: string
}

export type PaymentKind = 'DEPOSIT' | 'BALANCE' | 'REFUND'
export type PaymentMethod = 'PIX' | 'CASH' | 'CARD_LINK'
export type PaymentRecordStatus = 'PENDING_REVIEW' | 'CONFIRMED' | 'REJECTED'

export interface PaymentRecord {
  id: number
  kind: PaymentKind
  method: PaymentMethod
  amount: Money
  status: PaymentRecordStatus
  proofFileId: number | null
  submittedBy: Actor
  notes: string | null
  reviewNote: string | null
  reviewedAt: string | null
  createdAt: string
}

export interface PaymentSummary {
  total: Money | null
  depositAmount: Money | null
  received: Money
  balanceDue: Money | null
  status: PaymentStatus
  proofUnderReview: boolean
  /** Fim do prazo do sinal (só enquanto aguarda o sinal; null = sem prazo). */
  depositDueAt: string | null
  payments: PaymentRecord[]
}

export interface OrderDetail {
  id: number
  trackingCode: string
  version: number
  customerName: string
  customerWhatsapp: string
  whatsappLink: string
  pickupDate: string
  pickupTime: string
  customCake: boolean
  items: OrderItemView[]
  fulfillmentType: FulfillmentType
  deliveryAddress: string | null
  deliveryDistrict: string | null
  deliveryReference: string | null
  sizeId: number | null
  sizeName: string | null
  flavors: Choice[]
  fillings: Choice[]
  decoration: string | null
  cakeText: string | null
  notes: string | null
  topperRequested: boolean
  topperTheme: string | null
  topperName: string | null
  topperAge: number | null
  commercialStatus: CommercialStatus
  paymentStatus: PaymentStatus
  productionStatus: ProductionStatus
  cancelledAt: string | null
  cancellationReason: string | null
  cancelledBy: Actor | null
  slotReserved: boolean
  files: FileInfo[]
  quotes: Quote[]
  payments: PaymentSummary
  history: HistoryEntry[]
  createdAt: string
  updatedAt: string
  customerEmail: string | null
  deliveryZone: DeliveryZone | null
  cakeCategoryId: number | null
  cakeCategoryName: string | null
  /** Preço-base do tamanho no momento do pedido (preço inicial da tabela). */
  sizePrice: Money | null
  /** Taxa de entrega sugerida pela modalidade; vazia = informar manualmente no orçamento. */
  suggestedDeliveryFee: Money | null
}

export interface QuoteItemInput {
  kind: QuoteItemKind
  name: string
  description: string
  quantity: Money
  unitPrice: Money
}

export interface TrackingQuote {
  id: number
  versionNumber: number
  status: QuoteStatus
  items: QuoteItem[]
  breakdown: QuoteBreakdown
  total: Money
  depositAmount: Money
  balanceAmount: Money
  notes: string | null
  validUntil: string | null
}

export interface ClientPaymentEntry {
  kind: PaymentKind
  method: PaymentMethod
  amount: Money
  status: PaymentRecordStatus
  reviewNote: string | null
  createdAt: string
  proofFileId: number | null
}

export interface ClientPayment {
  total: Money
  depositAmount: Money
  received: Money
  balanceDue: Money
  status: PaymentStatus
  pixKey: string | null
  pixHolderName: string | null
  /** Link de pagamento no cartão (só enquanto o sinal está pendente). */
  cardPaymentLink: string | null
  /** Fim do prazo para pagar o sinal; null = sem prazo. */
  depositDueAt: string | null
  canSubmitProof: boolean
  payments: ClientPaymentEntry[]
}

export interface TrackingView {
  trackingCode: string
  customerName: string
  whatsappMasked: string
  pickupDate: string
  pickupTime: string
  customCake: boolean
  items: OrderItemView[]
  fulfillmentType: FulfillmentType
  deliveryAddress: string | null
  deliveryDistrict: string | null
  deliveryReference: string | null
  sizeId: number | null
  sizeName: string | null
  flavors: Choice[]
  fillings: Choice[]
  decoration: string | null
  cakeText: string | null
  notes: string | null
  topperRequested: boolean
  topperTheme: string | null
  topperName: string | null
  topperAge: number | null
  photoCount: number
  commercialStatus: CommercialStatus
  paymentStatus: PaymentStatus
  productionStatus: ProductionStatus
  editable: boolean
  version: number
  quote: TrackingQuote | null
  payment: ClientPayment | null
  deliveryZone: DeliveryZone | null
  cakeCategoryId: number | null
  cakeCategoryName: string | null
  /** A cliente informou e-mail (recebe lembretes de pagamento). */
  hasEmail: boolean
}

export interface PickupItem {
  id: number
  trackingCode: string
  version: number
  customerName: string
  customerWhatsapp: string
  whatsappLink: string
  pickupDate: string
  pickupTime: string
  sizeName: string | null
  summary: string
  fulfillmentType: FulfillmentType
  deliveryAddress: string | null
  deliveryDistrict: string | null
  cakeText: string | null
  topperRequested: boolean
  productionStatus: ProductionStatus
  paymentStatus: PaymentStatus
  total: Money | null
  balanceDue: Money | null
  /** A data de retirada já passou e o bolo não foi entregue. */
  overdue: boolean
}

export interface PickupBoard {
  today: string
  until: string
  orders: PickupItem[]
}
