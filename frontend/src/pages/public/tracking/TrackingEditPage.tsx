import { useCallback, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { Loading } from '../../../components/Loading'
import { useLoader } from '../../../hooks/useLoader'
import { PublicLayout } from '../../../layouts/PublicLayout'
import { ApiError } from '../../../services/api'
import { getAvailability, getBakery, getCatalog } from '../../../services/publicApi'
import { currentTracking, editTrackedOrder } from '../../../services/tracking'
import type { TrackingView } from '../../../types/order'
import type { PublicProduct } from '../../../types/product'
import type { Availability, BakeryInfo, PublicCatalog } from '../../../types/public'
import { initialOrderForm, toPayload, validateStep, type FieldErrors, type OrderFormState } from '../order/orderForm'
import styles from '../order/order.module.css'
import { StepCake } from '../order/steps/StepCake'
import { StepItems } from '../order/steps/StepItems'
import { StepSchedule } from '../order/steps/StepSchedule'
import { StepTopper } from '../order/steps/StepTopper'

function toForm(view: TrackingView): OrderFormState {
  return {
    ...initialOrderForm,
    customerName: view.customerName,
    customCake: view.customCake,
    items: view.items.map((i) => ({ productId: i.productId, quantity: i.quantity })),
    pickupDate: view.pickupDate,
    pickupTime: view.pickupTime,
    fulfillmentType: view.fulfillmentType,
    deliveryAddress: view.deliveryAddress ?? '',
    deliveryDistrict: view.deliveryDistrict ?? '',
    deliveryReference: view.deliveryReference ?? '',
    deliveryZone: view.deliveryZone ?? 'CITY',
    cakeCategoryId: view.cakeCategoryId,
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
 * antecedência, item ou produto desativado). Elas continuam disponíveis para a cliente manter, com os
 * dados copiados no pedido.
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

  const keptProducts: PublicProduct[] = view.items
    .filter((item) => !catalog.products.some((p) => p.id === item.productId && p.available))
    .map((item) => ({
      id: item.productId, categoryId: -1, name: item.name, description: null, price: item.unitPrice,
      unitLabel: item.unitLabel, minQuantity: 1, available: true, orderable: true, seasonal: false,
      availableFrom: null, availableUntil: null, availableDays: [], pickupAllowed: true, deliveryAllowed: true,
      photoIds: [],
    }))
  const products = [...catalog.products.filter((p) => !keptProducts.some((k) => k.id === p.id)), ...keptProducts]
  const categories = keptProducts.length > 0
    ? [...catalog.categories, { id: -1, name: 'Já no seu pedido', description: null }]
    : catalog.categories

  const sizes = view.sizeId == null || catalog.sizes.some((s) => s.id === view.sizeId)
    ? catalog.sizes
    : [...catalog.sizes, { id: view.sizeId, name: view.sizeName ?? '', description: null, servings: null, basePrice: '0.00',
        maxFlavors: Math.max(view.flavors.length, 1), maxFillings: Math.max(view.fillings.length, 1),
        categoryId: null, weightKg: null, diameterCm: null }]
  // Bolo "consulte valores" de uma categoria que saiu do site: continua disponível para manter.
  const cakeCategories = view.cakeCategoryId == null || view.sizeId != null
    || catalog.cakeCategories.some((c) => c.id === view.cakeCategoryId)
    ? catalog.cakeCategories
    : [...catalog.cakeCategories, { id: view.cakeCategoryId, name: view.cakeCategoryName ?? 'Seu bolo', description: null,
        notice: null, consultOnly: true, maxFlavors: Math.max(view.flavors.length, 1),
        maxFillings: Math.max(view.fillings.length, 1) }]

  return {
    availability: { ...availability, days },
    catalog: {
      ...catalog,
      customCakeEnabled: catalog.customCakeEnabled || view.customCake,
      sizes,
      cakeCategories,
      flavors: missing(catalog.flavors, view.flavors, (c) => ({ ...c, description: null })),
      fillings: missing(catalog.fillings, view.fillings, (c) => ({ ...c, description: null })),
      products,
      categories,
    },
  }
}

async function loadEditData() {
  const [view, catalog, availability, bakery] = await Promise.all([
    currentTracking(), getCatalog(), getAvailability(), getBakery(),
  ])
  return view ? { view, bakery, ...withCurrentChoices(view, catalog, availability) } : null
}

interface EditFormProps {
  view: TrackingView
  catalog: PublicCatalog
  availability: Availability
  bakery: BakeryInfo
}

function EditForm({ view, catalog, availability, bakery }: EditFormProps) {
  const navigate = useNavigate()
  const [form, setForm] = useState(() => toForm(view))
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string>()
  const [saving, setSaving] = useState(false)
  const update = useCallback((patch: Partial<OrderFormState>) => setForm((current) => ({ ...current, ...patch })), [])
  const showItems = catalog.products.some((p) => p.available)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const context = {
      size: catalog.sizes.find((s) => s.id === form.sizeId),
      category: catalog.cakeCategories.find((c) => c.id === form.cakeCategoryId),
      products: catalog.products,
    }
    const stepErrors = {
      ...(showItems ? validateStep('items', form, context) : {}),
      ...(form.customCake ? validateStep('cake', form, context) : {}),
      ...(form.customCake ? validateStep('topper', form, context) : {}),
      ...validateStep('schedule', form, context),
    }
    setErrors(stepErrors)
    setFormError(undefined)
    if (Object.keys(stepErrors).length > 0) {
      setFormError('Alguns campos precisam de correção.')
      return
    }
    setSaving(true)
    try {
      const { customerName: _name, customerWhatsapp: _phone, ...payload } = toPayload(form)
      await editTrackedOrder({ ...payload, version: view.version })
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

  const props = { form, errors, update, catalog, availability, bakery, allowPhotos: false }
  return (
    <form className={styles.page} onSubmit={handleSubmit} noValidate>
      {view.commercialStatus === 'QUOTE_SENT' && (
        <Alert tone="info">Ao salvar, o orçamento atual deixa de valer e a confeitaria vai preparar um novo.</Alert>
      )}
      {showItems && <StepItems {...props} />}
      {form.customCake && <StepCake {...props} />}
      {form.customCake && <StepTopper {...props} />}
      <StepSchedule {...props} />
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
