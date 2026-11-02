import { useState, type FormEvent } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { TextAreaField } from '../../../components/TextAreaField'
import { TextField } from '../../../components/TextField'
import { saveSize } from '../../../services/catalog'
import type { CakeSize } from '../../../types/catalog'
import { parseMoneyInput, toMoneyInput } from '../../../utils/money'
import styles from '../panel.module.css'
import { describeSaveError } from '../saveErrors'

interface SizeFormProps {
  size?: CakeSize
  onSaved: (size: CakeSize) => void
  onCancel: () => void
  onStale: () => void
}

type Errors = Partial<Record<'name' | 'description' | 'servings' | 'basePrice' | 'maxFlavors' | 'maxFillings' | 'displayOrder', string>>

function parseInteger(value: string, min: number, max: number): number | null {
  if (!/^\d+$/.test(value.trim())) return null
  const number = Number(value)
  return number >= min && number <= max ? number : null
}

export function SizeForm({ size, onSaved, onCancel, onStale }: SizeFormProps) {
  const [name, setName] = useState(size?.name ?? '')
  const [description, setDescription] = useState(size?.description ?? '')
  const [servings, setServings] = useState(size?.servings?.toString() ?? '')
  const [basePrice, setBasePrice] = useState(toMoneyInput(size?.basePrice))
  const [maxFlavors, setMaxFlavors] = useState(String(size?.maxFlavors ?? 1))
  const [maxFillings, setMaxFillings] = useState(String(size?.maxFillings ?? 1))
  const [displayOrder, setDisplayOrder] = useState(String(size?.displayOrder ?? 0))
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string>()
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const price = parseMoneyInput(basePrice)
    const servingsValue = servings.trim() === '' ? null : parseInteger(servings, 1, 500)
    const flavors = parseInteger(maxFlavors, 1, 10)
    const fillings = parseInteger(maxFillings, 1, 10)
    const order = parseInteger(displayOrder, 0, 9999)

    const nextErrors: Errors = {
      name: name.trim() ? undefined : 'Informe o nome.',
      basePrice: price == null ? 'Informe um valor válido, ex.: 120,00.' : undefined,
      servings: servings.trim() !== '' && servingsValue == null ? 'Use um número entre 1 e 500.' : undefined,
      maxFlavors: flavors == null ? 'Use um número entre 1 e 10.' : undefined,
      maxFillings: fillings == null ? 'Use um número entre 1 e 10.' : undefined,
      displayOrder: order == null ? 'Use um número de 0 a 9999.' : undefined,
    }
    setErrors(nextErrors)
    setFormError(undefined)
    if (Object.values(nextErrors).some(Boolean)) return

    setSaving(true)
    try {
      const saved = await saveSize(
        {
          name: name.trim(),
          description: description.trim(),
          servings: servingsValue,
          basePrice: price!,
          maxFlavors: flavors!,
          maxFillings: fillings!,
          displayOrder: order!,
          version: size?.version,
        },
        size?.id,
      )
      onSaved(saved)
    } catch (error) {
      const failure = describeSaveError(error)
      setErrors(failure.fields)
      setFormError(failure.message)
      if (failure.stale) onStale()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className={styles.formCard}>
      <h3>{size ? `Editar tamanho "${size.name}"` : 'Novo tamanho'}</h3>
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}
        <TextField label="Nome" value={name} onChange={(e) => setName(e.target.value)} error={errors.name} maxLength={80} autoFocus />
        <TextAreaField
          label="Descrição (opcional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          error={errors.description}
          maxLength={300}
          rows={2}
        />
        <div className={styles.row}>
          <TextField
            label="Preço-base (R$)"
            inputMode="decimal"
            value={basePrice}
            onChange={(e) => setBasePrice(e.target.value)}
            error={errors.basePrice}
            hint="Valor de referência. O preço final é definido no orçamento."
            placeholder="0,00"
          />
          <TextField
            label="Serve quantas pessoas (opcional)"
            inputMode="numeric"
            value={servings}
            onChange={(e) => setServings(e.target.value)}
            error={errors.servings}
          />
        </div>
        <div className={`${styles.row} ${styles.row3}`}>
          <TextField
            label="Massas que a cliente pode escolher"
            inputMode="numeric"
            value={maxFlavors}
            onChange={(e) => setMaxFlavors(e.target.value)}
            error={errors.maxFlavors}
          />
          <TextField
            label="Recheios que a cliente pode escolher"
            inputMode="numeric"
            value={maxFillings}
            onChange={(e) => setMaxFillings(e.target.value)}
            error={errors.maxFillings}
          />
          <TextField
            label="Ordem na lista"
            inputMode="numeric"
            value={displayOrder}
            onChange={(e) => setDisplayOrder(e.target.value)}
            error={errors.displayOrder}
            hint="Menor número aparece primeiro."
          />
        </div>
        <div className={styles.actions}>
          <Button type="submit" loading={saving}>
            Salvar
          </Button>
          <Button variant="secondary" onClick={onCancel} disabled={saving}>
            Cancelar
          </Button>
        </div>
      </form>
    </div>
  )
}
