import type {
  CommercialStatus,
  OrderDetail,
  OrderSummary,
  Page,
  PaymentKind,
  PaymentMethod,
  PaymentSummary,
  Quote,
  QuoteItemInput,
} from '../types/order'
import type { OrderPayload } from '../types/public'
import { apiFetch } from './api'

const BASE = '/admin/orders'

export interface OrderListQuery {
  status?: CommercialStatus
  q?: string
  page?: number
}

export function listOrders(query: OrderListQuery): Promise<Page<OrderSummary>> {
  const params = new URLSearchParams()
  if (query.status) params.set('status', query.status)
  if (query.q?.trim()) params.set('q', query.q.trim())
  params.set('page', String(query.page ?? 0))
  params.set('size', '20')
  return apiFetch<Page<OrderSummary>>(`${BASE}?${params}`)
}

export function orderCounts(): Promise<Record<CommercialStatus, number>> {
  return apiFetch<Record<CommercialStatus, number>>(`${BASE}/counts`)
}

export function getOrder(id: number): Promise<OrderDetail> {
  return apiFetch<OrderDetail>(`${BASE}/${id}`)
}

export function updateOrder(id: number, payload: OrderPayload & { version: number }): Promise<OrderDetail> {
  return apiFetch<OrderDetail>(`${BASE}/${id}`, { method: 'PUT', json: payload })
}

export function rejectOrder(id: number, reason: string, version: number): Promise<OrderDetail> {
  return apiFetch<OrderDetail>(`${BASE}/${id}/reject`, { json: { reason, version } })
}

export function cancelOrder(id: number, reason: string, version: number): Promise<OrderDetail> {
  return apiFetch<OrderDetail>(`${BASE}/${id}/cancel`, { json: { reason, version } })
}

/** URL de um arquivo do pedido (servido só para a proprietária logada). */
export function orderFileUrl(orderId: number, fileId: number): string {
  return `/api${BASE}/${orderId}/files/${fileId}`
}

export function saveQuoteDraft(
  orderId: number,
  draft: { items: QuoteItemInput[]; notes: string; validityHours: number | null },
): Promise<Quote> {
  return apiFetch<Quote>(`${BASE}/${orderId}/quotes/draft`, { method: 'PUT', json: draft })
}

export function deleteQuoteDraft(orderId: number): Promise<void> {
  return apiFetch<void>(`${BASE}/${orderId}/quotes/draft`, { method: 'DELETE' })
}

export function sendQuote(orderId: number): Promise<Quote> {
  return apiFetch<Quote>(`${BASE}/${orderId}/quotes/send`, { method: 'POST' })
}

export function registerPayment(
  orderId: number,
  payment: { kind: PaymentKind; method: PaymentMethod; amount: string; notes: string; acceptAsDeposit: boolean },
): Promise<PaymentSummary> {
  return apiFetch<PaymentSummary>(`${BASE}/${orderId}/payments`, { json: payment })
}

export function confirmPayment(paymentId: number, acceptAsDeposit: boolean): Promise<PaymentSummary> {
  return apiFetch<PaymentSummary>(`/admin/payments/${paymentId}/confirm`, { json: { acceptAsDeposit } })
}

export function rejectPayment(paymentId: number, reason: string): Promise<PaymentSummary> {
  return apiFetch<PaymentSummary>(`/admin/payments/${paymentId}/reject`, { json: { reason } })
}
