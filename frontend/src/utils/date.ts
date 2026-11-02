/**
 * Datas da agenda trafegam como "AAAA-MM-DD" (sem fuso) e horários como "HH:mm".
 * As funções abaixo nunca convertem para o fuso do navegador, evitando que uma data "mude de dia".
 */

export const BAKERY_TIME_ZONE = 'America/Sao_Paulo'

/** ISO-8601: índice 1 = segunda ... 7 = domingo. */
export const WEEKDAY_NAMES = ['', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo'] as const
const WEEKDAY_SHORT = ['', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'] as const

function parts(iso: string): [number, number, number] {
  const [year, month, day] = iso.split('-').map(Number)
  return [year, month, day]
}

function toUtcDate(iso: string): Date {
  const [year, month, day] = parts(iso)
  return new Date(Date.UTC(year, month - 1, day))
}

function fromUtcDate(date: Date): string {
  return date.toISOString().slice(0, 10)
}

/** Data de hoje no fuso da confeitaria. */
export function todayIso(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: BAKERY_TIME_ZONE }).format(now)
}

export function addDays(iso: string, days: number): string {
  const date = toUtcDate(iso)
  date.setUTCDate(date.getUTCDate() + days)
  return fromUtcDate(date)
}

/** 1 = segunda ... 7 = domingo. */
export function isoWeekday(iso: string): number {
  const day = toUtcDate(iso).getUTCDay()
  return day === 0 ? 7 : day
}

/** "2026-11-03" → "03/11/2026" */
export function formatDate(iso: string): string {
  const [year, month, day] = parts(iso)
  return `${String(day).padStart(2, '0')}/${String(month).padStart(2, '0')}/${year}`
}

/** "2026-11-03" → "Ter, 03/11" */
export function formatShortDay(iso: string): string {
  const [, month, day] = parts(iso)
  return `${WEEKDAY_SHORT[isoWeekday(iso)]}, ${String(day).padStart(2, '0')}/${String(month).padStart(2, '0')}`
}

/** "2026-11-03" → "Terça, 03/11/2026" */
export function formatLongDay(iso: string): string {
  return `${WEEKDAY_NAMES[isoWeekday(iso)]}, ${formatDate(iso)}`
}
