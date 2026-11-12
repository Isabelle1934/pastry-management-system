import { useRef, useState, type FormEvent } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { SelectField } from '../../../components/SelectField'
import { TextAreaField } from '../../../components/TextAreaField'
import { TextField } from '../../../components/TextField'
import { addProductPhoto, adminPhotoUrl, removeProductPhoto, saveProduct } from '../../../services/products'
import type { Product, ProductCategory } from '../../../types/product'
import { WEEKDAY_NAMES } from '../../../utils/date'
import { parseMoneyInput, toMoneyInput } from '../../../utils/money'
import { validatePhoto } from '../../public/order/orderForm'
import panel from '../panel.module.css'
import { describeSaveError } from '../saveErrors'
import styles from './catalog.module.css'

const MAX_PHOTOS = 6

interface ProductFormProps {
  product?: Product
  categories: ProductCategory[]
  onSaved: (product: Product, closeForm: boolean) => void
  onCancel: () => void
  onStale: () => void
}

type Errors = Record<string, string | undefined>

function parseInteger(value: string, min: number, max: number): number | null {
  if (!/^\d+$/.test(value.trim())) return null
  const number = Number(value)
  return number >= min && number <= max ? number : null
}

/** Cadastro e edição de produto, com fotos (as fotos ficam disponíveis depois do primeiro salvamento). */
export function ProductForm({ product, categories, onSaved, onCancel, onStale }: ProductFormProps) {
  const [current, setCurrent] = useState<Product | undefined>(product)
  const [categoryId, setCategoryId] = useState(String(product?.categoryId ?? categories.find((c) => c.active)?.id ?? ''))
  const [name, setName] = useState(product?.name ?? '')
  const [description, setDescription] = useState(product?.description ?? '')
  const [quoteOnly, setQuoteOnly] = useState(product ? product.price == null : false)
  const [price, setPrice] = useState(toMoneyInput(product?.price ?? null))
  const [unitLabel, setUnitLabel] = useState(product?.unitLabel ?? '')
  const [minQuantity, setMinQuantity] = useState(String(product?.minQuantity ?? 1))
  const [orderable, setOrderable] = useState(product?.orderable ?? true)
  const [seasonal, setSeasonal] = useState(product?.seasonal ?? false)
  const [availableFrom, setAvailableFrom] = useState(product?.availableFrom ?? '')
  const [availableUntil, setAvailableUntil] = useState(product?.availableUntil ?? '')
  const [days, setDays] = useState<number[]>(product?.availableDays ?? [])
  const [pickupAllowed, setPickupAllowed] = useState(product?.pickupAllowed ?? true)
  const [deliveryAllowed, setDeliveryAllowed] = useState(product?.deliveryAllowed ?? false)
  const [displayOrder, setDisplayOrder] = useState(String(product?.displayOrder ?? 0))
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const parsedPrice = quoteOnly ? null : parseMoneyInput(price)
    const min = parseInteger(minQuantity, 1, 9999)
    const order = parseInteger(displayOrder, 0, 9999)
    const nextErrors: Errors = {
      categoryId: categoryId ? undefined : 'Escolha a categoria.',
      name: name.trim() ? undefined : 'Informe o nome.',
      price: !quoteOnly && parsedPrice == null ? 'Informe um valor válido, ex.: 3,50 — ou marque "sob orçamento".' : undefined,
      minQuantity: min == null ? 'Use um número de 1 a 9999.' : undefined,
      displayOrder: order == null ? 'Use um número de 0 a 9999.' : undefined,
      availableUntil: availableFrom && availableUntil && availableUntil < availableFrom
        ? 'A data final precisa ser igual ou depois da inicial.' : undefined,
      pickupAllowed: !pickupAllowed && !deliveryAllowed ? 'Escolha pelo menos uma forma: retirada ou delivery.' : undefined,
    }
    setErrors(nextErrors)
    setFormError(undefined)
    setNotice(undefined)
    if (Object.values(nextErrors).some(Boolean)) return

    setSaving(true)
    try {
      const saved = await saveProduct(
        {
          categoryId: Number(categoryId),
          name: name.trim(),
          description: description.trim(),
          price: parsedPrice,
          unitLabel: unitLabel.trim(),
          minQuantity: min!,
          orderable,
          seasonal,
          availableFrom: availableFrom || null,
          availableUntil: availableUntil || null,
          availableDays: days,
          pickupAllowed,
          deliveryAllowed,
          displayOrder: order!,
          version: current?.version,
        },
        current?.id,
      )
      const isNew = current == null
      setCurrent(saved)
      // Produto novo: o formulário continua aberto para adicionar as fotos.
      onSaved(saved, !isNew)
      if (isNew) setNotice('Produto salvo. Agora você pode adicionar fotos.')
    } catch (error) {
      const failure = describeSaveError(error)
      setErrors(failure.fields)
      setFormError(failure.message)
      if (failure.stale) onStale()
    } finally {
      setSaving(false)
    }
  }

  async function upload(files: FileList | null) {
    if (!files || !current) return
    setFormError(undefined)
    setUploading(true)
    try {
      let latest = current
      for (const file of Array.from(files).slice(0, MAX_PHOTOS - current.photoIds.length)) {
        const invalid = validatePhoto(file)
        if (invalid) {
          setFormError(invalid)
          continue
        }
        latest = await addProductPhoto(current.id, file)
      }
      setCurrent(latest)
      onSaved(latest, false)
    } catch (error) {
      setFormError(describeSaveError(error).message ?? 'Não foi possível enviar a foto.')
    } finally {
      setUploading(false)
      if (fileInput.current) fileInput.current.value = ''
    }
  }

  async function removePhoto(fileId: number) {
    if (!current) return
    setUploading(true)
    try {
      const saved = await removeProductPhoto(current.id, fileId)
      setCurrent(saved)
      onSaved(saved, false)
    } catch (error) {
      setFormError(describeSaveError(error).message ?? 'Não foi possível remover a foto.')
    } finally {
      setUploading(false)
    }
  }

  const toggleDay = (day: number) =>
    setDays((selected) => (selected.includes(day) ? selected.filter((d) => d !== day) : [...selected, day].sort()))

  return (
    <div className={panel.formCard}>
      <h3>{current ? `Editar "${current.name}"` : 'Novo produto'}</h3>
      <form className={panel.form} onSubmit={handleSubmit} noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}
        {notice && <Alert tone="success">{notice}</Alert>}
        <div className={panel.row}>
          <TextField label="Nome" value={name} onChange={(e) => setName(e.target.value)} error={errors.name} maxLength={100} autoFocus />
          <SelectField label="Categoria" value={categoryId} onChange={(e) => setCategoryId(e.target.value)} error={errors.categoryId}
            options={[...(categoryId ? [] : [{ value: '', label: 'Escolha a categoria' }]),
              ...categories.filter((c) => c.active || String(c.id) === categoryId)
                .map((c) => ({ value: String(c.id), label: c.active ? c.name : `${c.name} (inativa)` }))]} />
        </div>
        <TextAreaField label="Descrição (opcional)" value={description} onChange={(e) => setDescription(e.target.value)}
          error={errors.description} maxLength={1000} rows={3} />

        <fieldset className={styles.group}>
          <legend>Preço</legend>
          <label className={styles.check}>
            <input type="checkbox" checked={quoteOnly} onChange={(e) => setQuoteOnly(e.target.checked)} />
            Preço sob orçamento (ex.: produtos personalizados)
          </label>
          <div className={panel.row}>
            {!quoteOnly && (
              <TextField label="Preço (R$)" inputMode="decimal" placeholder="3,50" value={price}
                onChange={(e) => setPrice(e.target.value)} error={errors.price} />
            )}
            <TextField label="Unidade (opcional)" placeholder="unidade, caixa com 12, kg…" value={unitLabel}
              onChange={(e) => setUnitLabel(e.target.value)} maxLength={40} />
            <TextField label="Pedido mínimo" inputMode="numeric" value={minQuantity}
              onChange={(e) => setMinQuantity(e.target.value)} error={errors.minQuantity} />
          </div>
        </fieldset>

        <fieldset className={styles.group}>
          <legend>Disponibilidade</legend>
          <label className={styles.check}>
            <input type="checkbox" checked={orderable} onChange={(e) => setOrderable(e.target.checked)} />
            Aceita encomendas pelo site
          </label>
          <label className={styles.check}>
            <input type="checkbox" checked={seasonal} onChange={(e) => setSeasonal(e.target.checked)} />
            Produto especial ou temporário (destaque “Especial”)
          </label>
          <div className={panel.row}>
            <TextField label="Disponível a partir de (opcional)" type="date" value={availableFrom}
              onChange={(e) => setAvailableFrom(e.target.value)} />
            <TextField label="Disponível até (opcional)" type="date" value={availableUntil}
              onChange={(e) => setAvailableUntil(e.target.value)} error={errors.availableUntil}
              hint="Depois dessa data o produto sai do catálogo sozinho." />
          </div>
          <p className={panel.description}>Dias da semana para retirada/entrega (nenhum marcado = todos os dias):</p>
          <div className={styles.days}>
            {[1, 2, 3, 4, 5, 6, 7].map((day) => (
              <label key={day} className={styles.dayChip}>
                <input type="checkbox" checked={days.includes(day)} onChange={() => toggleDay(day)} />
                {WEEKDAY_NAMES[day].slice(0, 3)}
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset className={styles.group}>
          <legend>Atendimento</legend>
          <label className={styles.check}>
            <input type="checkbox" checked={pickupAllowed} onChange={(e) => setPickupAllowed(e.target.checked)} />
            Retirada na confeitaria
          </label>
          <label className={styles.check}>
            <input type="checkbox" checked={deliveryAllowed} onChange={(e) => setDeliveryAllowed(e.target.checked)} />
            Delivery
          </label>
          {errors.pickupAllowed && <p className={styles.error} role="alert">{errors.pickupAllowed}</p>}
        </fieldset>

        <TextField label="Ordem de exibição" inputMode="numeric" value={displayOrder}
          onChange={(e) => setDisplayOrder(e.target.value)} error={errors.displayOrder}
          hint="Menor número aparece primeiro dentro da categoria." />

        <fieldset className={styles.group}>
          <legend>Fotos</legend>
          {!current ? (
            <p className={panel.description}>Salve o produto para adicionar fotos.</p>
          ) : (
            <>
              {current.photoIds.length > 0 && (
                <ul className={styles.photos}>
                  {current.photoIds.map((fileId, index) => (
                    <li key={fileId}>
                      <img src={adminPhotoUrl(current.id, fileId)} alt={`Foto ${index + 1} de ${current.name}`} />
                      {index === 0 && <span className={styles.cover}>Capa</span>}
                      <button type="button" className={styles.removePhoto} disabled={uploading}
                        onClick={() => void removePhoto(fileId)} aria-label={`Remover foto ${index + 1}`}>
                        Remover
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              {current.photoIds.length < MAX_PHOTOS && (
                <div>
                  <input ref={fileInput} id={`photo-${current.id}`} type="file" accept="image/jpeg,image/png,image/webp"
                    multiple className={styles.fileInput} onChange={(e) => void upload(e.target.files)} />
                  <label htmlFor={`photo-${current.id}`} className={styles.fileButton} aria-disabled={uploading}>
                    {uploading ? 'Enviando…' : 'Adicionar fotos'}
                  </label>
                  <p className={panel.description}>
                    Até {MAX_PHOTOS} fotos (JPG, PNG ou WEBP, até 5 MB cada). A primeira é a capa. Use somente fotos reais
                    dos seus produtos.
                  </p>
                </div>
              )}
            </>
          )}
        </fieldset>

        <div className={panel.actions}>
          <Button type="submit" loading={saving}>{current ? 'Salvar alterações' : 'Salvar produto'}</Button>
          <Button variant="secondary" onClick={onCancel} disabled={saving}>{current && !product ? 'Fechar' : 'Cancelar'}</Button>
        </div>
      </form>
    </div>
  )
}
