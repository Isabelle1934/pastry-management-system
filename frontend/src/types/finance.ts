import type { Money } from './catalog'

export interface ExpenseCategory {
  id: number
  name: string
  active: boolean
  version: number
}

export interface ExpenseOrderRef {
  id: number
  trackingCode: string
  customerName: string
}

export interface Expense {
  id: number
  description: string
  amount: Money
  expenseDate: string
  categoryId: number
  categoryName: string
  order: ExpenseOrderRef | null
  notes: string | null
  version: number
  createdAt: string
}

export interface ExpenseInput {
  description: string
  amount: Money
  expenseDate: string
  categoryId: number
  /** Código de acompanhamento do pedido, quando a despesa é de uma encomenda. */
  orderCode?: string
  notes?: string
  version?: number
}

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
  expenses: Money
  orderExpenses: Money
  expensesByCategory: { categoryId: number; name: string; total: Money }[]
  estimatedProfit: Money
  cashResult: Money
}
