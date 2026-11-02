import { useState, type FormEvent } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { TextField } from '../../../components/TextField'
import { useLoader } from '../../../hooks/useLoader'
import { getRules, saveRules } from '../../../services/schedule'
import type { ScheduleRules } from '../../../types/schedule'
import panel from '../panel.module.css'
import { describeSaveError } from '../saveErrors'
import styles from './schedule.module.css'

function RulesForm({ initial }: { initial: ScheduleRules }) {
  const [rules, setRules] = useState(initial)
  const [lead, setLead] = useState(String(initial.minLeadTimeHours))
  const [maxDays, setMaxDays] = useState(String(initial.maxBookingDaysAhead))
  const [errors, setErrors] = useState<Record<string, string | undefined>>({})
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string }>()
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const leadValue = /^\d+$/.test(lead) && Number(lead) <= 720 ? Number(lead) : null
    const daysValue = /^\d+$/.test(maxDays) && Number(maxDays) >= 1 && Number(maxDays) <= 365 ? Number(maxDays) : null
    const nextErrors = {
      minLeadTimeHours: leadValue == null ? 'Use um número de 0 a 720 horas.' : undefined,
      maxBookingDaysAhead: daysValue == null ? 'Use um número de 1 a 365 dias.' : undefined,
    }
    setErrors(nextErrors)
    setNotice(undefined)
    if (nextErrors.minLeadTimeHours || nextErrors.maxBookingDaysAhead) return

    setSaving(true)
    try {
      const saved = await saveRules({ minLeadTimeHours: leadValue!, maxBookingDaysAhead: daysValue!, version: rules.version })
      setRules(saved)
      setNotice({ tone: 'success', text: 'Regras salvas.' })
    } catch (error) {
      const failure = describeSaveError(error)
      setErrors(failure.fields)
      setNotice({
        tone: 'error',
        text: failure.stale
          ? 'As configurações foram alteradas em outra tela. Recarregue a página.'
          : (failure.message ?? 'Alguns campos precisam de correção.'),
      })
    } finally {
      setSaving(false)
    }
  }

  const leadDays = Number(lead) >= 24 && Number(lead) % 24 === 0 ? Number(lead) / 24 : undefined

  return (
    <form className={panel.form} onSubmit={handleSubmit} noValidate>
      <div className={panel.row}>
        <TextField
          label="Antecedência mínima (horas)"
          inputMode="numeric"
          value={lead}
          onChange={(e) => setLead(e.target.value)}
          error={errors.minLeadTimeHours}
          hint={
            leadDays
              ? `= ${leadDays} ${leadDays === 1 ? 'dia' : 'dias'}. A cliente não pode escolher horários antes disso.`
              : 'A cliente não pode escolher horários antes disso.'
          }
        />
        <TextField
          label="Aceitar encomendas até quantos dias à frente"
          inputMode="numeric"
          value={maxDays}
          onChange={(e) => setMaxDays(e.target.value)}
          error={errors.maxBookingDaysAhead}
        />
      </div>
      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
      <div className={panel.actions}>
        <Button type="submit" loading={saving}>
          Salvar regras
        </Button>
      </div>
    </form>
  )
}

export function RulesSection() {
  const { data, loading, error } = useLoader(getRules)
  return (
    <section className={panel.section} aria-labelledby="rules-title">
      <div className={panel.toolbar}>
        <h2 id="rules-title">Regras de agendamento</h2>
      </div>
      <p className={styles.muted}>Valem para os horários oferecidos no formulário de encomenda.</p>
      {loading && <p role="status">Carregando…</p>}
      {error && <Alert tone="error">{error}</Alert>}
      {data && <RulesForm initial={data} />}
    </section>
  )
}
