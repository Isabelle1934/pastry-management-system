import type { Page } from '../types/order'
import type { Expense, ExpenseCategory, ExpenseInput, FinanceSummary } from '../types/finance'
import { apiFetch } from './api'

const BASE = '/admin/finance'

export function getFinanceSummary(from: string, to: string): Promise<FinanceSummary> {
  return apiFetch<FinanceSummary>(`${BASE}/summary?from=${from}&to=${to}`)
}

export function listExpenses(from: string, to: string, page = 0): Promise<Page<Expense>> {
  return apiFetch<Page<Expense>>(`${BASE}/expenses?from=${from}&to=${to}&page=${page}&size=50`)
}

export function saveExpense(input: ExpenseInput, id?: number): Promise<Expense> {
  return id == null
    ? apiFetch<Expense>(`${BASE}/expenses`, { json: input })
    : apiFetch<Expense>(`${BASE}/expenses/${id}`, { method: 'PUT', json: input })
}

export function deleteExpense(expense: Expense): Promise<void> {
  return apiFetch<void>(`${BASE}/expenses/${expense.id}?version=${expense.version}`, { method: 'DELETE' })
}

export function listExpenseCategories(): Promise<ExpenseCategory[]> {
  return apiFetch<ExpenseCategory[]>(`${BASE}/categories`)
}

export function saveExpenseCategory(name: string, category?: ExpenseCategory): Promise<ExpenseCategory> {
  return category == null
    ? apiFetch<ExpenseCategory>(`${BASE}/categories`, { json: { name } })
    : apiFetch<ExpenseCategory>(`${BASE}/categories/${category.id}`, {
        method: 'PUT',
        json: { name, version: category.version },
      })
}

export function setExpenseCategoryActive(category: ExpenseCategory, active: boolean): Promise<ExpenseCategory> {
  return apiFetch<ExpenseCategory>(`${BASE}/categories/${category.id}/active`, {
    method: 'PATCH',
    json: { active, version: category.version },
  })
}
