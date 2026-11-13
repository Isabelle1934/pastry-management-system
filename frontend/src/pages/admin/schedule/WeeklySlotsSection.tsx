import { useState, type FormEvent } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { SelectField } from '../../../components/SelectField'
import { TextField } from '../../../components/TextField'
import { useLoader } from '../../../hooks/useLoader'
import { createSlot, deleteSlot, listSlots, updateSlot } from '../../../services/schedule'
import type { WeeklySlot } from '../../../types/schedule'
import { WEEKDAY_NAMES } from '../../../utils/date'
import panel from '../panel.module.css'
import { describeSaveError } from '../saveErrors'
import styles from './schedule.module.css'
import { Loading } from '../../../components/Loading'

const DAY_OPTIONS = [1, 2, 3, 4, 5, 6, 7].map((day) => ({ value: String(day), label: WEEKDAY_NAMES[day] }))

type Notice = { tone: 'success' | 'error'; text: string }

/** Vazio = sem limite; senão um número de 1 a 100. {@code undefined} = inválido. */
function parseCapacity(value: string): number | null | undefined {
  if (value.trim() === '') return null
  return /^\d+$/.test(value.trim()) && Number(value) >= 1 && Number(value) <= 100 ? Number(value) : undefined
}

function limitLabel(capacity: number | null): string {
  return capacity == null ? 'sem limite de pedidos' : `até ${capacity} ${capacity === 1 ? 'pedido' : 'pedidos'}`
}

function sortSlots(slots: WeeklySlot[]) {
  return [...slots].sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.startTime.localeCompare(b.startTime))
}

function AddSlotForm({ onCreated }: { onCreated: (slot: WeeklySlot) => void }) {
  const [day, setDay] = useState('5')
  const [time, setTime] = useState('')
  const [capacity, setCapacity] = useState('')
  const [errors, setErrors] = useState<Record<string, string | undefined>>({})
  const [formError, setFormError] = useState<string>()
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const capacityValue = parseCapacity(capacity)
    const nextErrors = {
      startTime: /^\d{2}:\d{2}$/.test(time) ? undefined : 'Informe o horário.',
      capacity: capacityValue === undefined ? 'Use um número de 1 a 100, ou deixe em branco para não limitar.' : undefined,
    }
    setErrors(nextErrors)
    setFormError(undefined)
    if (nextErrors.startTime || nextErrors.capacity) return

    setSaving(true)
    try {
      onCreated(await createSlot({ dayOfWeek: Number(day), startTime: time, capacity: capacityValue ?? null }))
      setTime('')
    } catch (error) {
      const failure = describeSaveError(error)
      setErrors(failure.fields)
      setFormError(failure.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className={panel.formCard}>
      <h3>Adicionar horário de retirada ou entrega</h3>
      <form className={panel.form} onSubmit={handleSubmit} noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}
        <div className={styles.inlineForm}>
          <SelectField label="Dia da semana" value={day} onChange={(e) => setDay(e.target.value)} options={DAY_OPTIONS} />
          <TextField label="Horário" type="time" value={time} onChange={(e) => setTime(e.target.value)} error={errors.startTime} />
          <TextField
            label="Limite de pedidos (opcional)"
            inputMode="numeric"
            value={capacity}
            onChange={(e) => setCapacity(e.target.value)}
            error={errors.capacity}
            hint="Em branco = sem limite."
          />
          <Button type="submit" loading={saving}>
            Adicionar
          </Button>
        </div>
      </form>
    </div>
  )
}

interface SlotRowProps {
  slot: WeeklySlot
  onChanged: (slot: WeeklySlot) => void
  onDeleted: (id: number) => void
  onNotice: (notice: Notice) => void
  onStale: () => void
}

function SlotRow({ slot, onChanged, onDeleted, onNotice, onStale }: SlotRowProps) {
  const [editing, setEditing] = useState(false)
  const [capacity, setCapacity] = useState(slot.capacity == null ? '' : String(slot.capacity))
  const [capacityError, setCapacityError] = useState<string>()
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [busy, setBusy] = useState(false)

  async function run(action: () => Promise<void>) {
    setBusy(true)
    try {
      await action()
    } catch (error) {
      const failure = describeSaveError(error)
      onNotice({ tone: 'error', text: failure.message ?? Object.values(failure.fields)[0] ?? 'Não foi possível salvar.' })
      if (failure.stale) onStale()
    } finally {
      setBusy(false)
    }
  }

  function saveCapacity(event: FormEvent) {
    event.preventDefault()
    const value = parseCapacity(capacity)
    if (value === undefined) {
      setCapacityError('Use um número de 1 a 100, ou deixe em branco para não limitar.')
      return
    }
    setCapacityError(undefined)
    void run(async () => {
      onChanged(await updateSlot(slot, { capacity: value, active: slot.active }))
      setEditing(false)
      onNotice({ tone: 'success', text: `Horário das ${slot.startTime}: ${limitLabel(value)}.` })
    })
  }

  return (
    <li className={styles.slotRow}>
      <span className={styles.slotInfo}>
        <span className={styles.slotTime}>{slot.startTime}</span>
        <span>
          {limitLabel(slot.capacity)}
        </span>
        {!slot.active && <span className={styles.chip}>Inativo</span>}
      </span>
      {editing ? (
        <form className={styles.inlineForm} onSubmit={saveCapacity} noValidate>
          <TextField
            label="Novo limite (em branco = sem limite)"
            inputMode="numeric"
            value={capacity}
            onChange={(e) => setCapacity(e.target.value)}
            error={capacityError}
            autoFocus
          />
          <Button type="submit" loading={busy}>
            Salvar
          </Button>
          <Button variant="secondary" onClick={() => setEditing(false)} disabled={busy}>
            Cancelar
          </Button>
        </form>
      ) : confirmingDelete ? (
        <span className={panel.actions}>
          <Button
            loading={busy}
            onClick={() =>
              void run(async () => {
                await deleteSlot(slot.id)
                onDeleted(slot.id)
                onNotice({ tone: 'success', text: `Horário das ${slot.startTime} removido.` })
              })
            }
          >
            Confirmar remoção
          </Button>
          <Button variant="secondary" onClick={() => setConfirmingDelete(false)} disabled={busy}>
            Manter
          </Button>
        </span>
      ) : (
        <span className={panel.actions}>
          <Button variant="secondary" onClick={() => setEditing(true)} disabled={busy}>
            Alterar limite
          </Button>
          <Button
            variant="secondary"
            loading={busy}
            onClick={() =>
              void run(async () => {
                const saved = await updateSlot(slot, { capacity: slot.capacity, active: !slot.active })
                onChanged(saved)
                onNotice({
                  tone: 'success',
                  text: saved.active ? `Horário das ${saved.startTime} reativado.` : `Horário das ${saved.startTime} desativado.`,
                })
              })
            }
          >
            {slot.active ? 'Desativar' : 'Ativar'}
          </Button>
          <Button variant="secondary" onClick={() => setConfirmingDelete(true)} disabled={busy}>
            Remover
          </Button>
        </span>
      )}
    </li>
  )
}

export function WeeklySlotsSection() {
  const { data: slots, loading, error, reload, setData } = useLoader(listSlots)
  const [notice, setNotice] = useState<Notice>()

  const replace = (saved: WeeklySlot) =>
    setData((current) => sortSlots([...(current ?? []).filter((s) => s.id !== saved.id), saved]))

  return (
    <section className={panel.section} aria-labelledby="weekly-title">
      <div className={panel.toolbar}>
        <h2 id="weekly-title">Horários da semana</h2>
      </div>
      <p className={styles.muted}>
        Os horários de retirada e entrega se repetem toda semana. Vários pedidos podem usar o mesmo horário; o limite é opcional (em branco = sem limite). Para fechar um dia (feriado, folga), use os bloqueios.
        Remover ou desativar um horário não cancela pedidos já confirmados.
      </p>
      <AddSlotForm
        onCreated={(slot) => {
          replace(slot)
          setNotice({ tone: 'success', text: `Horário de ${WEEKDAY_NAMES[slot.dayOfWeek].toLowerCase()} às ${slot.startTime} adicionado.` })
        }}
      />
      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
      {loading && <Loading />}
      {error && <Alert tone="error">{error}</Alert>}
      {slots && (
        <div className={styles.weekGrid}>
          {[1, 2, 3, 4, 5, 6, 7].map((day) => {
            const daySlots = slots.filter((slot) => slot.dayOfWeek === day)
            const open = daySlots.some((slot) => slot.active)
            return (
              <article key={day} className={`${styles.dayCard} ${open ? '' : styles.dayClosed}`}>
                <div className={styles.dayHeader}>
                  <h3 className={styles.dayTitle}>{WEEKDAY_NAMES[day]}</h3>
                  {!open && <span className={styles.chip}>Fechado</span>}
                </div>
                {daySlots.length === 0 ? (
                  <p className={styles.muted}>Sem horários de retirada.</p>
                ) : (
                  <ul className={styles.slotList}>
                    {daySlots.map((slot) => (
                      <SlotRow
                        key={slot.id}
                        slot={slot}
                        onChanged={replace}
                        onDeleted={(id) => setData((current) => current?.filter((s) => s.id !== id))}
                        onNotice={setNotice}
                        onStale={() => void reload()}
                      />
                    ))}
                  </ul>
                )}
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}
