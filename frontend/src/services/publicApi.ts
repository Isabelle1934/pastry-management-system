import type { Availability, BakeryInfo, OrderPayload, PublicCatalog } from '../types/public'
import { apiFetch } from './api'

export function getBakery(): Promise<BakeryInfo> {
  return apiFetch<BakeryInfo>('/public/bakery')
}

export function getCatalog(): Promise<PublicCatalog> {
  return apiFetch<PublicCatalog>('/public/catalog')
}

export function getAvailability(): Promise<Availability> {
  return apiFetch<Availability>('/public/availability')
}

/** Envia a solicitação em multipart: JSON do pedido + fotos opcionais. Devolve o código de acompanhamento. */
export function submitOrder(
  order: OrderPayload,
  referencePhotos: File[],
  topperPhoto: File | null,
): Promise<{ trackingCode: string }> {
  const form = new FormData()
  form.append('order', new Blob([JSON.stringify(order)], { type: 'application/json' }))
  referencePhotos.forEach((photo) => form.append('referencePhotos', photo))
  if (topperPhoto) form.append('topperPhoto', topperPhoto)
  return apiFetch<{ trackingCode: string }>('/public/orders', { method: 'POST', body: form })
}
