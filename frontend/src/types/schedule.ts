export interface WeeklySlot {
  id: number
  /** 1 = segunda ... 7 = domingo */
  dayOfWeek: number
  startTime: string
  /** Limite de pedidos confirmados; null = sem limite. */
  capacity: number | null
  active: boolean
  version: number
}

export interface ScheduleBlock {
  id: number
  date: string
  /** null = dia inteiro */
  startTime: string | null
  reason: string | null
  /** Pedidos já confirmados no período bloqueado (não são cancelados). */
  confirmedOrders: number
}

export interface ScheduleRules {
  minLeadTimeHours: number
  maxBookingDaysAhead: number
  version: number
}

export interface CalendarSlot {
  time: string
  /** Limite de pedidos confirmados; null = sem limite. */
  capacity: number | null
  reserved: number
  blocked: boolean
  blockReason: string | null
  tooSoon: boolean
  beyondLimit: boolean
  bookable: boolean
}

export interface CalendarDay {
  date: string
  dayOfWeek: number
  blockedAllDay: boolean
  blockReason: string | null
  slots: CalendarSlot[]
}
