import type { Money } from '../types/catalog'
import type { Page } from '../types/order'
import { apiFetch } from './api'

export interface CustomerSummary {
  name: string
  whatsapp: string
  /** Número sem "+", usado para filtrar os pedidos da cliente. */
  whatsappDigits: string
  whatsappLink: string
  orders: number
  confirmedOrders: number
  completedOrders: number
  totalPaid: Money
  firstOrderAt: string
  lastOrderAt: string
  lastPickupDate: string
}

export function listCustomers(q: string, page: number): Promise<Page<CustomerSummary>> {
  const params = new URLSearchParams({ page: String(page), size: '20' })
  if (q.trim()) params.set('q', q.trim())
  return apiFetch<Page<CustomerSummary>>(`/admin/customers?${params}`)
}
