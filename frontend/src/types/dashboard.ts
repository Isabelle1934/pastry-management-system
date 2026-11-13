import type { Money } from './catalog'
import type { PickupItem } from './order'

export interface Dashboard {
  today: string
  actions: {
    newOrders: number
    proofsToReview: number
    awaitingClientResponse: number
    awaitingDeposit: number
    overduePickups: number
  }
  production: { notStarted: number; inProduction: number; ready: number; pickupsToday: number }
  upcomingPickups: PickupItem[]
  expiringQuotes: { orderId: number; customerName: string; validUntil: string }[]
  month: { from: string; to: string; forecastRevenue: Money; received: Money; pending: Money; profit: Money }
  /** Pedidos com data no mês: total (sem recusados/cancelados), pendentes e confirmados. */
  monthOrders: { total: number; pending: number; confirmed: number }
  unreadNotifications: number
}
