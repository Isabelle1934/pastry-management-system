import type { HealthResponse } from '../types/api'
import { apiFetch } from './api'

export function getHealth(): Promise<HealthResponse> {
  return apiFetch<HealthResponse>('/public/health')
}
