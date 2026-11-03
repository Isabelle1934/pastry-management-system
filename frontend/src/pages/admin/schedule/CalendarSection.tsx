import { useCallback, useState } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { useLoader } from '../../../hooks/useLoader'
import { getCalendar } from '../../../services/schedule'
import type { CalendarSlot } from '../../../types/schedule'
import { addDays, formatShortDay, todayIso } from '../../../utils/date'
import panel from '../panel.module.css'
import styles from './schedule.module.css'
import { Loading } from '../../../components/Loading'

const PAGE_DAYS = 14

function slotStatus(slot: CalendarSlot): { label: string; tone: 'ok' | 'warn' | 'neutral' } {
  if (slot.blocked) return { label: 'Bloqueado', tone: 'warn' }
  if (slot.reserved >= slot.capacity) return { label: 'Lotado', tone: 'warn' }
  if (slot.tooSoon) return { label: 'Prazo encerrado', tone: 'neutral' }
  if (slot.beyondLimit) return { label: 'Ainda não aberto', tone: 'neutral' }
  return { label: 'Disponível', tone: 'ok' }
}

/** Visão dos próximos dias: vagas confirmadas por horário e por que um horário não está disponível. */
export function CalendarSection() {
  const [from, setFrom] = useState(todayIso)
  const to = addDays(from, PAGE_DAYS - 1)
  const load = useCallback(() => getCalendar(from, to), [from, to])
  const { data: days, loading, error } = useLoader(load)
  const isFirstPage = from === todayIso()

  return (
    <section className={panel.section} aria-labelledby="calendar-title">
      <div className={panel.toolbar}>
        <h2 id="calendar-title">Próximos dias</h2>
      </div>
      <p className={styles.muted}>
        Somente pedidos com sinal confirmado ocupam vaga. Solicitações ainda sem sinal não aparecem nesta contagem.
      </p>
      <div className={styles.pager}>
        <Button variant="secondary" onClick={() => setFrom(addDays(from, -PAGE_DAYS))} disabled={isFirstPage}>
          ← Anteriores
        </Button>
        <span>
          {formatShortDay(from)} a {formatShortDay(to)}
        </span>
        <Button variant="secondary" onClick={() => setFrom(addDays(from, PAGE_DAYS))}>
          Próximos →
        </Button>
      </div>

      {loading && <Loading />}
      {error && <Alert tone="error">{error}</Alert>}
      {days && days.every((day) => day.slots.length === 0) && (
        <p className={panel.empty}>
          Nenhum horário de retirada nestes dias. Cadastre os horários da semana na aba “Horários da semana”.
        </p>
      )}
      {days && days.some((day) => day.slots.length > 0) && (
        <div className={`${styles.weekGrid} ${styles.calendarGrid}`}>
          {days
            .filter((day) => day.slots.length > 0 || day.blockedAllDay)
            .map((day) => (
              <article key={day.date} className={`${styles.dayCard} ${day.blockedAllDay ? styles.dayClosed : ''}`}>
                <div className={styles.dayHeader}>
                  <h3 className={styles.dayTitle}>{formatShortDay(day.date)}</h3>
                  {day.blockedAllDay && (
                    <span className={`${styles.chip} ${styles.chipWarn}`}>
                      Dia bloqueado{day.blockReason ? `: ${day.blockReason}` : ''}
                    </span>
                  )}
                </div>
                <ul className={styles.slotList}>
                  {day.slots.map((slot) => {
                    const status = slotStatus(slot)
                    return (
                      <li key={slot.time} className={styles.slotRow}>
                        <span className={styles.slotTime}>{slot.time}</span>
                        <span className={styles.slotInfo}>
                          <span>
                            {slot.reserved} de {slot.capacity} {slot.capacity === 1 ? 'vaga' : 'vagas'}
                          </span>
                          <span
                            className={`${styles.chip} ${status.tone === 'ok' ? styles.chipOk : status.tone === 'warn' ? styles.chipWarn : ''}`}
                          >
                            {status.label}
                          </span>
                        </span>
                      </li>
                    )
                  })}
                </ul>
              </article>
            ))}
        </div>
      )}
    </section>
  )
}
