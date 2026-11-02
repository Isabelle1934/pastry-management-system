import { useState, type FormEvent } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { SelectField } from '../../../components/SelectField'
import { TextField } from '../../../components/TextField'
import { useLoader } from '../../../hooks/useLoader'
import { createBlock, deleteBlock, listBlocks, listSlots } from '../../../services/schedule'
import type { ScheduleBlock, WeeklySlot } from '../../../types/schedule'
import { formatLongDay, isoWeekday, todayIso } from '../../../utils/date'
import panel from '../panel.module.css'
import { describeSaveError } from '../saveErrors'
import styles from './schedule.module.css'

const WHOLE_DAY = ''

function sortBlocks(blocks: ScheduleBlock[]) {
  return [...blocks].sort((a, b) => a.date.localeCompare(b.date) || (a.startTime ?? '').localeCompare(b.startTime ?? ''))
}

function confirmedWarning(block: ScheduleBlock): string | undefined {
  if (block.confirmedOrders === 0) return undefined
  const plural = block.confirmedOrders === 1 ? 'pedido confirmado' : 'pedidos confirmados'
  return `Atenção: já existe(m) ${block.confirmedOrders} ${plural} nesse período. O bloqueio não cancela pedidos — entre em contato com a cliente se precisar remarcar.`
}

function AddBlockForm({ slots, onCreated }: { slots: WeeklySlot[]; onCreated: (block: ScheduleBlock) => void }) {
  const [date, setDate] = useState('')
  const [time, setTime] = useState(WHOLE_DAY)
  const [reason, setReason] = useState('')
  const [errors, setErrors] = useState<Record<string, string | undefined>>({})
  const [formError, setFormError] = useState<string>()
  const [saving, setSaving] = useState(false)

  const timesForDay = date ? slots.filter((slot) => slot.dayOfWeek === isoWeekday(date)).map((slot) => slot.startTime) : []
  const timeOptions = [
    { value: WHOLE_DAY, label: 'Dia inteiro' },
    ...timesForDay.map((t) => ({ value: t, label: `Somente às ${t}` })),
  ]

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const nextErrors = { date: date ? undefined : 'Escolha a data.' }
    setErrors(nextErrors)
    setFormError(undefined)
    if (nextErrors.date) return

    setSaving(true)
    try {
      onCreated(await createBlock({ date, startTime: time === WHOLE_DAY ? null : time, reason: reason.trim() }))
      setDate('')
      setTime(WHOLE_DAY)
      setReason('')
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
      <h3>Bloquear data ou horário</h3>
      <form className={panel.form} onSubmit={handleSubmit} noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}
        <div className={styles.inlineForm}>
          <TextField
            label="Data"
            type="date"
            min={todayIso()}
            value={date}
            onChange={(e) => {
              setDate(e.target.value)
              setTime(WHOLE_DAY)
            }}
            error={errors.date}
          />
          <SelectField
            label="O que bloquear"
            value={time}
            onChange={(e) => setTime(e.target.value)}
            options={timeOptions}
            error={errors.startTime}
          />
          <TextField
            label="Motivo (opcional)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={200}
            placeholder="Ex.: feriado, viagem"
          />
          <Button type="submit" loading={saving}>
            Bloquear
          </Button>
        </div>
      </form>
    </div>
  )
}

export function BlocksSection() {
  const { data: blocks, loading, error, setData } = useLoader(listBlocks)
  const { data: slots } = useLoader(listSlots)
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string }>()
  const [confirmingId, setConfirmingId] = useState<number>()
  const [busyId, setBusyId] = useState<number>()

  async function remove(block: ScheduleBlock) {
    setBusyId(block.id)
    try {
      await deleteBlock(block.id)
      setData((current) => current?.filter((b) => b.id !== block.id))
      setNotice({ tone: 'success', text: `Bloqueio de ${formatLongDay(block.date)} removido.` })
    } catch (err) {
      setNotice({ tone: 'error', text: describeSaveError(err).message ?? 'Não foi possível remover.' })
    } finally {
      setBusyId(undefined)
      setConfirmingId(undefined)
    }
  }

  return (
    <section className={panel.section} aria-labelledby="blocks-title">
      <div className={panel.toolbar}>
        <h2 id="blocks-title">Bloqueios</h2>
      </div>
      <p className={styles.muted}>Datas ou horários bloqueados deixam de aparecer para as clientes.</p>
      <AddBlockForm
        slots={slots ?? []}
        onCreated={(block) => {
          setData((current) => sortBlocks([...(current ?? []), block]))
          const warning = confirmedWarning(block)
          setNotice(warning ? { tone: 'error', text: warning } : { tone: 'success', text: 'Bloqueio criado.' })
        }}
      />
      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
      {loading && <p role="status">Carregando…</p>}
      {error && <Alert tone="error">{error}</Alert>}
      {blocks && blocks.length === 0 && <p className={panel.empty}>Nenhum bloqueio programado.</p>}
      {blocks && blocks.length > 0 && (
        <ul className={panel.list}>
          {blocks.map((block) => (
            <li key={block.id} className={panel.card}>
              <div className={styles.dayHeader}>
                <div>
                  <h3 className={panel.cardTitle}>{formatLongDay(block.date)}</h3>
                  <p className={styles.muted}>
                    {block.startTime ? `Somente às ${block.startTime}` : 'Dia inteiro'}
                    {block.reason ? ` · ${block.reason}` : ''}
                  </p>
                </div>
                {confirmingId === block.id ? (
                  <span className={panel.actions}>
                    <Button loading={busyId === block.id} onClick={() => void remove(block)}>
                      Confirmar
                    </Button>
                    <Button variant="secondary" onClick={() => setConfirmingId(undefined)}>
                      Manter
                    </Button>
                  </span>
                ) : (
                  <Button variant="secondary" onClick={() => setConfirmingId(block.id)}>
                    Remover bloqueio
                  </Button>
                )}
              </div>
              {confirmedWarning(block) && <Alert tone="error">{confirmedWarning(block)}</Alert>}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
