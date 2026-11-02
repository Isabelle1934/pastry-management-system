import type { Dashboard } from '../types/dashboard'
import { apiFetch } from './api'

export function getDashboard(): Promise<Dashboard> {
  return apiFetch<Dashboard>('/admin/dashboard')
}
