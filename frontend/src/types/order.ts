import type { Money } from './catalog'

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

export interface QuoteItem {
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
  sizeName: string
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
  purpose: 'ORDER_REFERENCE' | 'TOPPER_REFERENCE' | 'PAYMENT_PROOF'
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
export type PaymentMethod = 'PIX' | 'CASH'
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
  sizeId: number
  sizeName: string
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
}

export interface QuoteItemInput {
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
  total: Money
  depositAmount: Money
  balanceAmount: Money
  notes: string | null
  validUntil: string | null
}

export interface ClientPaymentEntry {
  kind: PaymentKind
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
  canSubmitProof: boolean
  payments: ClientPaymentEntry[]
}

export interface TrackingView {
  trackingCode: string
  customerName: string
  whatsappMasked: string
  pickupDate: string
  pickupTime: string
  sizeId: number
  sizeName: string
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
  sizeName: string
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
