import type { BakerySettings, BakerySettingsInput } from '../types/settings'
import { apiFetch } from './api'

export function getSettings(): Promise<BakerySettings> {
  return apiFetch<BakerySettings>('/admin/settings')
}

export function saveSettings(input: BakerySettingsInput): Promise<BakerySettings> {
  return apiFetch<BakerySettings>('/admin/settings', { method: 'PUT', json: input })
}
