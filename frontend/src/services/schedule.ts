import type { CalendarDay, ScheduleBlock, ScheduleRules, WeeklySlot } from '../types/schedule'
import { apiFetch } from './api'

const BASE = '/admin/schedule'

export function getRules(): Promise<ScheduleRules> {
  return apiFetch<ScheduleRules>(`${BASE}/rules`)
}

export function saveRules(rules: ScheduleRules): Promise<ScheduleRules> {
  return apiFetch<ScheduleRules>(`${BASE}/rules`, { method: 'PUT', json: rules })
}

export function listSlots(): Promise<WeeklySlot[]> {
  return apiFetch<WeeklySlot[]>(`${BASE}/slots`)
}

export function createSlot(input: { dayOfWeek: number; startTime: string; capacity: number }): Promise<WeeklySlot> {
  return apiFetch<WeeklySlot>(`${BASE}/slots`, { json: input })
}

export function updateSlot(slot: WeeklySlot, changes: { capacity: number; active: boolean }): Promise<WeeklySlot> {
  return apiFetch<WeeklySlot>(`${BASE}/slots/${slot.id}`, {
    method: 'PUT',
    json: { ...changes, version: slot.version },
  })
}

export function deleteSlot(id: number): Promise<void> {
  return apiFetch<void>(`${BASE}/slots/${id}`, { method: 'DELETE' })
}

export function listBlocks(): Promise<ScheduleBlock[]> {
  return apiFetch<ScheduleBlock[]>(`${BASE}/blocks`)
}

export function createBlock(input: { date: string; startTime: string | null; reason: string }): Promise<ScheduleBlock> {
  return apiFetch<ScheduleBlock>(`${BASE}/blocks`, { json: input })
}

export function deleteBlock(id: number): Promise<void> {
  return apiFetch<void>(`${BASE}/blocks/${id}`, { method: 'DELETE' })
}

export function getCalendar(from: string, to: string): Promise<CalendarDay[]> {
  return apiFetch<CalendarDay[]>(`${BASE}/calendar?from=${from}&to=${to}`)
}
