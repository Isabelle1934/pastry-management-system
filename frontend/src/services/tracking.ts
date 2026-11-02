import type { TrackingView } from '../types/order'
import { ApiError, apiFetch } from './api'

/** Pedido já verificado nesta sessão, ou null se for preciso informar código e dígitos. */
export async function currentTracking(): Promise<TrackingView | null> {
  try {
    return await apiFetch<TrackingView>('/tracking/order')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

export function verifyTracking(trackingCode: string, whatsappLast4: string): Promise<TrackingView> {
  return apiFetch<TrackingView>('/tracking/verify', { json: { trackingCode, whatsappLast4 } })
}

export function editTrackedOrder(payload: Record<string, unknown>): Promise<TrackingView> {
  return apiFetch<TrackingView>('/tracking/order', { method: 'PUT', json: payload })
}

export function acceptQuote(quoteId: number): Promise<TrackingView> {
  return apiFetch<TrackingView>(`/tracking/order/quotes/${quoteId}/accept`, { method: 'POST' })
}

export function declineQuote(quoteId: number, reason: string): Promise<TrackingView> {
  return apiFetch<TrackingView>(`/tracking/order/quotes/${quoteId}/decline`, { json: { reason } })
}

export function leaveTracking(): Promise<void> {
  return apiFetch<void>('/tracking/logout', { method: 'POST' })
}
