import type { FinanceSummary } from '../types/finance'
import { apiFetch } from './api'

export function getFinanceSummary(from: string, to: string): Promise<FinanceSummary> {
  return apiFetch<FinanceSummary>(`/admin/finance/summary?from=${from}&to=${to}`)
}
