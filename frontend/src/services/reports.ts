import type { Money } from '../types/catalog'
import type { CommercialStatus } from '../types/order'
import { apiFetch } from './api'

export interface Ranked {
  name: string
  group: string | null
  quantity: number
  orders: number
}

export interface Report {
  from: string
  to: string
  statusCounts: Record<CommercialStatus, number>
  totals: {
    orders: number
    sold: number
    lost: number
    soldRevenue: Money
    averageTicket: Money
    received: Money
  }
  topProducts: Ranked[]
  categories: Ranked[]
  cakeSizes: Ranked[]
  flavors: Ranked[]
  fillings: Ranked[]
  customCakes: number
  toppers: number
  fulfillment: { pickup: number; delivery: number }
}

export function getReport(from: string, to: string): Promise<Report> {
  return apiFetch<Report>(`/admin/reports?${new URLSearchParams({ from, to })}`)
}
