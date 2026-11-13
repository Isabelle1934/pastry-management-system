import type { Money } from './catalog'

/** Indicadores do período. Só pagamentos confirmados pela proprietária entram como recebidos. */
export interface FinanceSummary {
  from: string
  to: string
  forecastRevenue: Money
  forecastOrders: number
  awaitingDepositRevenue: Money
  awaitingDepositOrders: number
  received: Money
  receivedDeposits: Money
  receivedBalances: Money
  refunds: Money
  pending: Money
  /** Recebido líquido no período (sem cadastro de custos). */
  profit: Money
}
