import { useState, type FormEvent } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { SelectField } from '../../../components/SelectField'
import { TextAreaField } from '../../../components/TextAreaField'
import { TextField } from '../../../components/TextField'
import { useLoader } from '../../../hooks/useLoader'
import { listCakeCategories, listOptions, listSizes } from '../../../services/catalog'
import { listProducts } from '../../../services/products'
import { updateOrder } from '../../../services/orders'
import { listSlots } from '../../../services/schedule'
import type { CatalogOption } from '../../../types/catalog'
import type { OrderDetail } from '../../../types/order'
import type { DeliveryZone, FulfillmentType, OrderItemInput } from '../../../types/product'
import { setQuantity } from '../../../utils/cart'
import { DELIVERY_ZONE_LABELS, FULFILLMENT_LABELS } from '../../../utils/productRules'
import { isoWeekday } from '../../../utils/date'
import panel from '../panel.module.css'
import { describeSaveError } from '../saveErrors'
import styles from './orders.module.css'
import { Loading } from '../../../components/Loading'

async function loadEditData() {
  const [sizes, flavors, fillings, slots, products, cakeCategories] = await Promise.all([
    listSizes(),
    listOptions('flavors'),
    listOptions('fillings'),
    listSlots(),
    listProducts(),
    listCakeCategories(),
  ])
  return { sizes, flavors, fillings, slots, products, cakeCategories }
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
    customerEmail: order.customerEmail ?? '',
    pickupDate: order.pickupDate,
    pickupTime: order.pickupTime,
    customCake: order.customCake,
    items: order.items.map((i) => ({ productId: i.productId, quantity: i.quantity, notes: i.notes ?? '' })) as OrderItemInput[],
    fulfillmentType: order.fulfillmentType as FulfillmentType,
    deliveryAddress: order.deliveryAddress ?? '',
    deliveryDistrict: order.deliveryDistrict ?? '',
    deliveryReference: order.deliveryReference ?? '',
    deliveryZone: (order.deliveryZone ?? 'CITY') as DeliveryZone,
    cakeCategoryId: order.cakeCategoryId,
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
  const [productToAdd, setProductToAdd] = useState('')

  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) =>
    setForm((current) => ({ ...current, [key]: value }))

  if (loading) return <Loading />
  if (error || !data) return <Alert tone="error">{error ?? 'Não foi possível carregar.'}</Alert>

  const size = data.sizes.find((s) => s.id === form.sizeId)
  const consultCategory = form.sizeId == null
    ? data.cakeCategories.find((c) => c.id === form.cakeCategoryId && c.consultOnly) : undefined
  const limits = size ?? consultCategory
  const times = data.slots
    .filter((slot) => slot.active && form.pickupDate && slot.dayOfWeek === isoWeekday(form.pickupDate))
    .map((slot) => slot.startTime)
  const timeOptions = [...new Set([...(form.pickupDate === order.pickupDate ? [order.pickupTime] : []), ...times])]

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(undefined)
    setSaving(true)
    try {
      const cake = form.customCake
      const topper = cake && form.topperRequested
      const delivery = form.fulfillmentType === 'DELIVERY'
      const saved = await updateOrder(order.id, {
        customerName: form.customerName,
        customerWhatsapp: form.customerWhatsapp,
        customerEmail: form.customerEmail,
        pickupDate: form.pickupDate,
        pickupTime: form.pickupTime,
        customCake: cake,
        sizeId: cake ? form.sizeId : null,
        flavorIds: cake ? form.flavorIds : [],
        fillingIds: cake ? form.fillingIds : [],
        decoration: cake ? form.decoration : '',
        cakeText: cake ? form.cakeText : '',
        notes: form.notes,
        topperRequested: topper,
        topperTheme: topper ? form.topperTheme : '',
        topperName: topper ? form.topperName : '',
        topperAge: topper && form.topperAge.trim() ? Number(form.topperAge) : null,
        items: form.items,
        fulfillmentType: form.fulfillmentType,
        deliveryAddress: delivery ? form.deliveryAddress : '',
        deliveryDistrict: delivery ? form.deliveryDistrict : '',
        deliveryReference: delivery ? form.deliveryReference : '',
        deliveryZone: delivery ? form.deliveryZone : null,
        cakeCategoryId: cake ? form.cakeCategoryId : null,
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
          <TextField label="E-mail (opcional)" type="email" value={form.customerEmail}
            onChange={(e) => set('customerEmail', e.target.value)} error={errors.customerEmail} maxLength={254} />
        </div>
        <div className={panel.row}>
          <SelectField label="Atendimento" value={form.fulfillmentType} error={errors.fulfillmentType}
            onChange={(e) => set('fulfillmentType', e.target.value as FulfillmentType)}
            options={(['PICKUP', 'DELIVERY'] as const).map((v) => ({ value: v, label: FULFILLMENT_LABELS[v] }))} />
          {form.fulfillmentType === 'DELIVERY' && (
            <SelectField label="Modalidade da entrega" value={form.deliveryZone} error={errors.deliveryZone}
              onChange={(e) => set('deliveryZone', e.target.value as DeliveryZone)}
              options={(['CITY', 'PARTY_VENUE', 'OUTSIDE_CITY'] as const).map((v) => ({ value: v, label: DELIVERY_ZONE_LABELS[v] }))} />
          )}
        </div>
        {form.fulfillmentType === 'DELIVERY' && (
          <div className={panel.row}>
            <TextField label="Endereço de entrega" value={form.deliveryAddress} maxLength={300}
              onChange={(e) => set('deliveryAddress', e.target.value)} error={errors.deliveryAddress} />
            <TextField label="Bairro" value={form.deliveryDistrict} maxLength={100}
              onChange={(e) => set('deliveryDistrict', e.target.value)} />
            <TextField label="Referência" value={form.deliveryReference} maxLength={200}
              onChange={(e) => set('deliveryReference', e.target.value)} />
          </div>
        )}
        <div className={panel.row}>
          <TextField label="Data agendada" type="date" value={form.pickupDate}
            onChange={(e) => set('pickupDate', e.target.value)} error={errors.pickupDate} />
          <SelectField label="Horário" value={form.pickupTime} onChange={(e) => set('pickupTime', e.target.value)}
            error={errors.pickupTime}
            options={timeOptions.length ? timeOptions.map((t) => ({ value: t, label: t }))
              : [{ value: '', label: 'Sem horários neste dia' }]} />
        </div>
        <fieldset style={{ border: 'none', margin: 0, padding: 0 }}>
          <legend style={{ fontWeight: 600, padding: 0 }}>Produtos do catálogo</legend>
          {form.items.length === 0 && <p className={panel.description}>Nenhum produto do catálogo.</p>}
          {form.items.map((item) => {
            const original = order.items.find((i) => i.productId === item.productId)
            const product = data.products.find((p) => p.id === item.productId)
            return (
              <div key={item.productId} className={panel.row}>
                <TextField label={original?.name ?? product?.name ?? 'Produto'} inputMode="numeric"
                  value={String(item.quantity)}
                  onChange={(e) => set('items', setQuantity(form.items, item.productId,
                    Number(e.target.value.replace(/\D/g, '').slice(0, 4)) || 1))} />
                <Button variant="secondary" onClick={() => set('items', form.items.filter((i) => i.productId !== item.productId))}>
                  Remover
                </Button>
              </div>
            )
          })}
          <div className={panel.row}>
            <SelectField label="Adicionar produto" value={productToAdd} onChange={(e) => setProductToAdd(e.target.value)}
              options={[{ value: '', label: 'Escolha um produto' },
                ...data.products.filter((p) => p.status !== 'INACTIVE' && !form.items.some((i) => i.productId === p.id))
                  .map((p) => ({ value: String(p.id), label: `${p.name} (${p.categoryName})` }))]} />
            <Button variant="secondary" disabled={!productToAdd} onClick={() => {
              const product = data.products.find((p) => p.id === Number(productToAdd))
              if (product) set('items', setQuantity(form.items, product.id, product.minQuantity))
              setProductToAdd('')
            }}>Adicionar</Button>
          </div>
          {errors.items && <p role="alert" style={{ margin: 0, color: 'var(--color-danger)', fontSize: '0.9rem' }}>{errors.items}</p>}
        </fieldset>
        <label className={styles.check}>
          <input type="checkbox" checked={form.customCake} onChange={(e) => set('customCake', e.target.checked)} />
          Bolo personalizado
        </label>
        {form.customCake && (<>
        <SelectField label="Tamanho" error={errors.sizeId}
          value={form.sizeId != null ? `s${form.sizeId}` : consultCategory ? `c${consultCategory.id}` : ''}
          onChange={(e) => {
            const value = e.target.value
            const nextSize = value.startsWith('s') ? data.sizes.find((s) => s.id === Number(value.slice(1))) : undefined
            const nextCategory = value.startsWith('c')
              ? data.cakeCategories.find((c) => c.id === Number(value.slice(1))) : undefined
            const next = nextSize ?? nextCategory
            setForm((current) => ({
              ...current,
              sizeId: nextSize ? nextSize.id : null,
              cakeCategoryId: nextSize ? nextSize.categoryId : nextCategory ? nextCategory.id : null,
              flavorIds: next ? current.flavorIds.slice(0, next.maxFlavors) : current.flavorIds,
              fillingIds: next ? current.fillingIds.slice(0, next.maxFillings) : current.fillingIds,
            }))
          }}
          options={[...(form.sizeId == null && !consultCategory ? [{ value: '', label: 'Escolha o tamanho' }] : []),
            ...data.sizes.filter((s) => s.active || s.id === order.sizeId)
              .map((s) => ({
                value: `s${s.id}`,
                label: `${s.categoryName ? `${s.categoryName} – ` : ''}${s.name}${s.active ? '' : ' (inativo)'}`,
              })),
            ...data.cakeCategories.filter((c) => c.consultOnly && (c.active || c.id === order.cakeCategoryId))
              .map((c) => ({ value: `c${c.id}`, label: `${c.name} (consulte valores)` }))]} />
        <OptionChecks legend="Massas" options={data.flavors} selected={form.flavorIds} max={limits?.maxFlavors ?? 1}
          error={errors.flavorIds} onChange={(ids) => set('flavorIds', ids)} />
        <OptionChecks legend="Recheios" options={data.fillings} selected={form.fillingIds} max={limits?.maxFillings ?? 1}
          error={errors.fillingIds} onChange={(ids) => set('fillingIds', ids)} />
        <TextAreaField label="Decoração" value={form.decoration} onChange={(e) => set('decoration', e.target.value)}
          error={errors.decoration} maxLength={1000} />
        <TextField label="Nome ou frase no bolo" value={form.cakeText} onChange={(e) => set('cakeText', e.target.value)}
          error={errors.cakeText} maxLength={100} />
        </>)}
        <TextAreaField label="Observações" value={form.notes} onChange={(e) => set('notes', e.target.value)}
          error={errors.notes} maxLength={1000} />
        {form.customCake && (
          <label className={styles.check}>
            <input type="checkbox" checked={form.topperRequested} onChange={(e) => set('topperRequested', e.target.checked)} />
            Topo de bolo (cobrado à parte)
          </label>
        )}
        {form.customCake && form.topperRequested && (
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
