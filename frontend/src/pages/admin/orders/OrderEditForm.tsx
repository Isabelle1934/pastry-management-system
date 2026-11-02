import { useState, type FormEvent } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { SelectField } from '../../../components/SelectField'
import { TextAreaField } from '../../../components/TextAreaField'
import { TextField } from '../../../components/TextField'
import { useLoader } from '../../../hooks/useLoader'
import { listOptions, listSizes } from '../../../services/catalog'
import { updateOrder } from '../../../services/orders'
import { listSlots } from '../../../services/schedule'
import type { CatalogOption } from '../../../types/catalog'
import type { OrderDetail } from '../../../types/order'
import { isoWeekday } from '../../../utils/date'
import panel from '../panel.module.css'
import { describeSaveError } from '../saveErrors'
import styles from './orders.module.css'

async function loadEditData() {
  const [sizes, flavors, fillings, slots] = await Promise.all([
    listSizes(),
    listOptions('flavors'),
    listOptions('fillings'),
    listSlots(),
  ])
  return { sizes, flavors, fillings, slots }
}

interface OptionChecksProps {
  legend: string
  options: CatalogOption[]
  selected: number[]
  max: number
  error?: string
  onChange: (ids: number[]) => void
}

function OptionChecks({ legend, options, selected, max, error, onChange }: OptionChecksProps) {
  // Itens inativos aparecem só se já estiverem no pedido.
  const visible = options.filter((o) => o.active || selected.includes(o.id))
  return (
    <fieldset style={{ border: 'none', margin: 0, padding: 0 }}>
      <legend style={{ fontWeight: 600, padding: 0 }}>
        {legend} (até {max})
      </legend>
      <div className={styles.checkGrid}>
        {visible.map((option) => (
          <label key={option.id} className={styles.check}>
            <input
              type="checkbox"
              checked={selected.includes(option.id)}
              disabled={!selected.includes(option.id) && selected.length >= max}
              onChange={() =>
                onChange(selected.includes(option.id) ? selected.filter((id) => id !== option.id) : [...selected, option.id])
              }
            />
            {option.name}
            {!option.active && ' (inativo)'}
          </label>
        ))}
      </div>
      {error && (
        <p role="alert" style={{ margin: 0, color: 'var(--color-danger)', fontSize: '0.9rem' }}>
          {error}
        </p>
      )}
    </fieldset>
  )
}

interface Props {
  order: OrderDetail
  onSaved: (order: OrderDetail) => void
  onCancel: () => void
  onStale: () => void
}

/** Edição completa pela proprietária (inclusive depois do aceite do orçamento). */
export function OrderEditForm({ order, onSaved, onCancel, onStale }: Props) {
  const { data, loading, error } = useLoader(loadEditData)
  const [form, setForm] = useState({
    customerName: order.customerName,
    customerWhatsapp: order.customerWhatsapp,
    pickupDate: order.pickupDate,
    pickupTime: order.pickupTime,
    sizeId: order.sizeId,
    flavorIds: order.flavors.map((f) => f.id),
    fillingIds: order.fillings.map((f) => f.id),
    decoration: order.decoration ?? '',
    cakeText: order.cakeText ?? '',
    notes: order.notes ?? '',
    topperRequested: order.topperRequested,
    topperTheme: order.topperTheme ?? '',
    topperName: order.topperName ?? '',
    topperAge: order.topperAge?.toString() ?? '',
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string>()
  const [saving, setSaving] = useState(false)

  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) =>
    setForm((current) => ({ ...current, [key]: value }))

  if (loading) return <p role="status">Carregando…</p>
  if (error || !data) return <Alert tone="error">{error ?? 'Não foi possível carregar.'}</Alert>

  const size = data.sizes.find((s) => s.id === form.sizeId)
  const times = data.slots
    .filter((slot) => slot.active && form.pickupDate && slot.dayOfWeek === isoWeekday(form.pickupDate))
    .map((slot) => slot.startTime)
  const timeOptions = [...new Set([...(form.pickupDate === order.pickupDate ? [order.pickupTime] : []), ...times])]

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(undefined)
    setSaving(true)
    try {
      const saved = await updateOrder(order.id, {
        customerName: form.customerName,
        customerWhatsapp: form.customerWhatsapp,
        pickupDate: form.pickupDate,
        pickupTime: form.pickupTime,
        sizeId: form.sizeId,
        flavorIds: form.flavorIds,
        fillingIds: form.fillingIds,
        decoration: form.decoration,
        cakeText: form.cakeText,
        notes: form.notes,
        topperRequested: form.topperRequested,
        topperTheme: form.topperRequested ? form.topperTheme : '',
        topperName: form.topperRequested ? form.topperName : '',
        topperAge: form.topperRequested && form.topperAge.trim() ? Number(form.topperAge) : null,
        version: order.version,
      })
      onSaved(saved)
    } catch (err) {
      const failure = describeSaveError(err)
      setErrors(failure.fields)
      setFormError(failure.message)
      if (failure.stale) onStale()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className={panel.formCard}>
      <h3>Editar pedido</h3>
      <form className={panel.form} onSubmit={handleSubmit} noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}
        {order.slotReserved && (
          <Alert tone="info">Este pedido já ocupa vaga na agenda. Se mudar data/horário, a vaga será remarcada.</Alert>
        )}
        <div className={panel.row}>
          <TextField label="Nome da cliente" value={form.customerName} onChange={(e) => set('customerName', e.target.value)}
            error={errors.customerName} maxLength={120} />
          <TextField label="WhatsApp" type="tel" value={form.customerWhatsapp}
            onChange={(e) => set('customerWhatsapp', e.target.value)} error={errors.customerWhatsapp} />
        </div>
        <div className={panel.row}>
          <TextField label="Data de retirada" type="date" value={form.pickupDate}
            onChange={(e) => set('pickupDate', e.target.value)} error={errors.pickupDate} />
          <SelectField label="Horário" value={form.pickupTime} onChange={(e) => set('pickupTime', e.target.value)}
            error={errors.pickupTime}
            options={timeOptions.length ? timeOptions.map((t) => ({ value: t, label: t }))
              : [{ value: '', label: 'Sem horários neste dia' }]} />
        </div>
        <SelectField label="Tamanho" value={String(form.sizeId)} error={errors.sizeId}
          onChange={(e) => {
            const next = data.sizes.find((s) => s.id === Number(e.target.value))
            setForm((current) => ({
              ...current,
              sizeId: Number(e.target.value),
              flavorIds: next ? current.flavorIds.slice(0, next.maxFlavors) : current.flavorIds,
              fillingIds: next ? current.fillingIds.slice(0, next.maxFillings) : current.fillingIds,
            }))
          }}
          options={data.sizes.filter((s) => s.active || s.id === order.sizeId)
            .map((s) => ({ value: String(s.id), label: s.active ? s.name : `${s.name} (inativo)` }))} />
        <OptionChecks legend="Massas" options={data.flavors} selected={form.flavorIds} max={size?.maxFlavors ?? 1}
          error={errors.flavorIds} onChange={(ids) => set('flavorIds', ids)} />
        <OptionChecks legend="Recheios" options={data.fillings} selected={form.fillingIds} max={size?.maxFillings ?? 1}
          error={errors.fillingIds} onChange={(ids) => set('fillingIds', ids)} />
        <TextAreaField label="Decoração" value={form.decoration} onChange={(e) => set('decoration', e.target.value)}
          error={errors.decoration} maxLength={1000} />
        <TextField label="Nome ou frase no bolo" value={form.cakeText} onChange={(e) => set('cakeText', e.target.value)}
          error={errors.cakeText} maxLength={100} />
        <TextAreaField label="Observações" value={form.notes} onChange={(e) => set('notes', e.target.value)}
          error={errors.notes} maxLength={1000} />
        <label className={styles.check}>
          <input type="checkbox" checked={form.topperRequested} onChange={(e) => set('topperRequested', e.target.checked)} />
          Topo de bolo
        </label>
        {form.topperRequested && (
          <div className={panel.row}>
            <TextField label="Tema do topo" value={form.topperTheme} onChange={(e) => set('topperTheme', e.target.value)}
              error={errors.topperTheme} maxLength={100} />
            <TextField label="Nome no topo" value={form.topperName} onChange={(e) => set('topperName', e.target.value)}
              maxLength={60} />
            <TextField label="Idade" inputMode="numeric" value={form.topperAge}
              onChange={(e) => set('topperAge', e.target.value)} error={errors.topperAge} maxLength={3} />
          </div>
        )}
        <div className={panel.actions}>
          <Button type="submit" loading={saving}>Salvar alterações</Button>
          <Button variant="secondary" onClick={onCancel} disabled={saving}>Cancelar</Button>
        </div>
      </form>
    </div>
  )
}
