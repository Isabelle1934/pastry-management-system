const MONTHS = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro',
]

/** Mês no formato "2026-11". */
export type MonthKey = string

export function isMonthKey(value: string | null): value is MonthKey {
  return value != null && /^\d{4}-(0[1-9]|1[0-2])$/.test(value)
}

/** Mês corrente no fuso da confeitaria. */
export function currentMonth(now = new Date()): MonthKey {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit' })
    .formatToParts(now)
  const get = (type: string) => parts.find((p) => p.type === type)?.value
  return `${get('year')}-${get('month')}`
}

/** "2026-11" → { from: "2026-11-01", to: "2026-11-30" } */
export function monthRange(month: MonthKey): { from: string; to: string } {
  const [year, monthNumber] = month.split('-').map(Number)
  const lastDay = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate()
  return { from: `${month}-01`, to: `${month}-${String(lastDay).padStart(2, '0')}` }
}

/** "2026-12" + 1 → "2027-01" */
export function shiftMonth(month: MonthKey, delta: number): MonthKey {
  const [year, monthNumber] = month.split('-').map(Number)
  const index = year * 12 + (monthNumber - 1) + delta
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`
}

/** "2026-11" → "Novembro de 2026" */
export function formatMonth(month: MonthKey): string {
  const [year, monthNumber] = month.split('-').map(Number)
  return `${MONTHS[monthNumber - 1]} de ${year}`
}
