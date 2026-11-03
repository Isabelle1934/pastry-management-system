import { useCallback, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { useLoader } from '../../../hooks/useLoader'
import { PublicLayout } from '../../../layouts/PublicLayout'
import { ApiError } from '../../../services/api'
import { getAvailability, getCatalog } from '../../../services/publicApi'
import { currentTracking, editTrackedOrder } from '../../../services/tracking'
import type { TrackingView } from '../../../types/order'
import type { Availability, PublicCatalog } from '../../../types/public'
import { initialOrderForm, toPayload, validateStep, type FieldErrors, type OrderFormState } from '../order/orderForm'
import styles from '../order/order.module.css'
import { StepCake } from '../order/steps/StepCake'
import { StepSchedule } from '../order/steps/StepSchedule'
import { StepTopper } from '../order/steps/StepTopper'
import { Loading } from '../../../components/Loading'

function toForm(view: TrackingView): OrderFormState {
  return {
    ...initialOrderForm,
    customerName: view.customerName,
    pickupDate: view.pickupDate,
    pickupTime: view.pickupTime,
    sizeId: view.sizeId,
    flavorIds: view.flavors.map((f) => f.id),
    fillingIds: view.fillings.map((f) => f.id),
    decoration: view.decoration ?? '',
    cakeText: view.cakeText ?? '',
    notes: view.notes ?? '',
    topperRequested: view.topperRequested,
    topperTheme: view.topperTheme ?? '',
    topperName: view.topperName ?? '',
    topperAge: view.topperAge?.toString() ?? '',
  }
}

/**
 * O pedido pode ter escolhas que não aparecem mais para novas encomendas (horário agora dentro da
 * antecedência, item desativado). Elas continuam disponíveis para a cliente manter.
 */
function withCurrentChoices(view: TrackingView, catalog: PublicCatalog, availability: Availability) {
  const days = availability.days.some((d) => d.date === view.pickupDate)
    ? availability.days.map((d) =>
        d.date === view.pickupDate && !d.times.includes(view.pickupTime)
          ? { ...d, times: [...d.times, view.pickupTime].sort() }
          : d,
      )
    : [...availability.days, { date: view.pickupDate, times: [view.pickupTime] }].sort((a, b) => a.date.localeCompare(b.date))
  const missing = <T extends { id: number }>(list: T[], current: { id: number; name: string }[], make: (c: { id: number; name: string }) => T) =>
    [...list, ...current.filter((c) => !list.some((o) => o.id === c.id)).map(make)]
  return {
    availability: { ...availability, days },
    catalog: {
      sizes: catalog.sizes.some((s) => s.id === view.sizeId)
        ? catalog.sizes
        : [...catalog.sizes, { id: view.sizeId, name: view.sizeName, description: null, servings: null, basePrice: '0.00',
            maxFlavors: Math.max(view.flavors.length, 1), maxFillings: Math.max(view.fillings.length, 1) }],
      flavors: missing(catalog.flavors, view.flavors, (c) => ({ ...c, description: null })),
      fillings: missing(catalog.fillings, view.fillings, (c) => ({ ...c, description: null })),
    },
  }
}

async function loadEditData() {
  const [view, catalog, availability] = await Promise.all([currentTracking(), getCatalog(), getAvailability()])
  return view ? { view, ...withCurrentChoices(view, catalog, availability) } : null
}

function EditForm({ view, catalog, availability }: { view: TrackingView; catalog: PublicCatalog; availability: Availability }) {
  const navigate = useNavigate()
  const [form, setForm] = useState(() => toForm(view))
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string>()
  const [saving, setSaving] = useState(false)
  const update = useCallback((patch: Partial<OrderFormState>) => setForm((current) => ({ ...current, ...patch })), [])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const size = catalog.sizes.find((s) => s.id === form.sizeId)
    const stepErrors = { ...validateStep(1, form, size), ...validateStep(2, form, size), ...validateStep(3, form, size) }
    setErrors(stepErrors)
    setFormError(undefined)
    if (Object.keys(stepErrors).length > 0) {
      setFormError('Alguns campos precisam de correção.')
      return
    }
    setSaving(true)
    try {
      const payload = toPayload(form)
      await editTrackedOrder({
        pickupDate: payload.pickupDate, pickupTime: payload.pickupTime, sizeId: payload.sizeId,
        flavorIds: payload.flavorIds, fillingIds: payload.fillingIds, decoration: payload.decoration,
        cakeText: payload.cakeText, notes: payload.notes, topperRequested: payload.topperRequested,
        topperTheme: payload.topperTheme, topperName: payload.topperName, topperAge: payload.topperAge,
        version: view.version,
      })
      navigate('/acompanhar', { state: { saved: true } })
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setErrors(err.fieldErrors as FieldErrors)
        setFormError('Alguns campos precisam de correção.')
      } else {
        setFormError(err instanceof ApiError ? err.message : 'Não foi possível salvar. Tente novamente.')
      }
    } finally {
      setSaving(false)
    }
  }

  const props = { form, errors, update, catalog, availability, allowPhotos: false }
  return (
    <form className={styles.page} onSubmit={handleSubmit} noValidate>
      {view.commercialStatus === 'QUOTE_SENT' && (
        <Alert tone="info">Ao salvar, o orçamento atual deixa de valer e a confeitaria vai preparar um novo.</Alert>
      )}
      <StepSchedule {...props} />
      <StepCake {...props} />
      <StepTopper {...props} />
      <p className={styles.hint}>Para trocar as fotos enviadas, fale com a confeitaria pelo WhatsApp.</p>
      {formError && <Alert tone="error">{formError}</Alert>}
      <div className={styles.nav}>
        <Link to="/acompanhar">Cancelar</Link>
        <Button type="submit" loading={saving}>Salvar alterações</Button>
      </div>
    </form>
  )
}

export function TrackingEditPage() {
  const { data, loading, error } = useLoader(loadEditData)

  if (!loading && !error && data === null) return <Navigate to="/acompanhar" replace />

  return (
    <PublicLayout>
      <div className={styles.page}>
        <h1>Alterar pedido</h1>
        {loading && <Loading />}
        {error && <Alert tone="error">{error}</Alert>}
        {data && !data.view.editable && (
          <Alert tone="info">
            Este pedido não pode mais ser alterado por aqui. <Link to="/acompanhar">Voltar</Link>
          </Alert>
        )}
        {data && data.view.editable && <EditForm {...data} />}
      </div>
    </PublicLayout>
  )
}
