import type { OrderDetail, PickupBoard, ProductionStatus } from '../types/order'
import { apiFetch } from './api'

export interface ProductionChange {
  status: ProductionStatus
  version: number
  note?: string
  /** Necessário para entregar com saldo em aberto. */
  confirmBalanceDue?: boolean
}

export function getPickupBoard(days: number): Promise<PickupBoard> {
  return apiFetch<PickupBoard>(`/admin/production?days=${days}`)
}

export function changeProduction(orderId: number, change: ProductionChange): Promise<OrderDetail> {
  return apiFetch<OrderDetail>(`/admin/orders/${orderId}/production`, { json: change })
}
