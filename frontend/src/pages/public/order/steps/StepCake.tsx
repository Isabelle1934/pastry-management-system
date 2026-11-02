import { TextAreaField } from '../../../../components/TextAreaField'
import { TextField } from '../../../../components/TextField'
import type { PublicOption } from '../../../../types/public'
import { formatBRL } from '../../../../utils/money'
import { PhotoPicker } from '../PhotoPicker'
import { applySize, MAX_REFERENCE_PHOTOS, toggleChoice } from '../orderForm'
import type { StepProps } from './stepProps'
import styles from '../order.module.css'

interface OptionGroupProps {
  legend: string
  name: string
  options: PublicOption[]
  selected: number[]
  max: number
  error?: string
  onChange: (ids: number[]) => void
}

function OptionGroup({ legend, name, options, selected, max, error, onChange }: OptionGroupProps) {
  const single = max === 1
  return (
    <fieldset className={styles.fieldset}>
      <legend className={styles.legend}>
        {legend} {single ? '(escolha 1)' : `(até ${max})`}
      </legend>
      <div className={styles.choiceGrid}>
        {options.map((option) => {
          const checked = selected.includes(option.id)
          const limitReached = !single && !checked && selected.length >= max
          return (
            <label key={option.id} className={styles.choice}>
              <input
                type={single ? 'radio' : 'checkbox'}
                name={name}
                checked={checked}
                disabled={limitReached}
                onChange={() => onChange(toggleChoice(selected, option.id, max))}
              />
              <span className={styles.choiceTitle}>{option.name}</span>
              {option.description && <span className={styles.choiceMeta}>{option.description}</span>}
            </label>
          )
        })}
      </div>
      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
    </fieldset>
  )
}

export function StepCake({ form, errors, update, catalog }: StepProps) {
  const size = catalog.sizes.find((s) => s.id === form.sizeId)

  return (
    <section className={styles.card} aria-labelledby="step-title">
      <h2 id="step-title">Seu bolo</h2>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Tamanho</legend>
        <div className={styles.choiceGrid}>
          {catalog.sizes.map((option) => (
            <label key={option.id} className={styles.choice}>
              <input
                type="radio"
                name="sizeId"
                checked={form.sizeId === option.id}
                onChange={() => update(applySize(form, option))}
              />
              <span className={styles.choiceTitle}>{option.name}</span>
              {option.servings != null && <span className={styles.choiceMeta}>Serve {option.servings} pessoas</span>}
              <span className={styles.choiceMeta}>a partir de {formatBRL(option.basePrice)}</span>
              {option.description && <span className={styles.choiceMeta}>{option.description}</span>}
            </label>
          ))}
        </div>
        <p className={styles.hint}>O valor final é definido no orçamento, de acordo com a personalização.</p>
        {errors.sizeId && (
          <p className={styles.error} role="alert">
            {errors.sizeId}
          </p>
        )}
      </fieldset>

      {size ? (
        <>
          <OptionGroup
            legend="Massa"
            name="flavor"
            options={catalog.flavors}
            selected={form.flavorIds}
            max={size.maxFlavors}
            error={errors.flavorIds}
            onChange={(flavorIds) => update({ flavorIds })}
          />
          <OptionGroup
            legend="Recheio"
            name="filling"
            options={catalog.fillings}
            selected={form.fillingIds}
            max={size.maxFillings}
            error={errors.fillingIds}
            onChange={(fillingIds) => update({ fillingIds })}
          />
        </>
      ) : (
        <p className={styles.hint}>Escolha o tamanho para ver as opções de massa e recheio.</p>
      )}

      <TextAreaField
        label="Decoração"
        hint="Descreva cores, tema, estilo… Se tiver uma referência, envie a foto abaixo."
        value={form.decoration}
        onChange={(e) => update({ decoration: e.target.value })}
        error={errors.decoration}
        maxLength={1000}
      />
      <TextField
        label="Nome ou frase no bolo (opcional)"
        value={form.cakeText}
        onChange={(e) => update({ cakeText: e.target.value })}
        error={errors.cakeText}
        maxLength={100}
      />
      <TextAreaField
        label="Observações (opcional)"
        value={form.notes}
        onChange={(e) => update({ notes: e.target.value })}
        error={errors.notes}
        maxLength={1000}
      />
      <PhotoPicker
        label="Fotos de referência (opcional)"
        hint={`Até ${MAX_REFERENCE_PHOTOS} fotos em JPG, PNG ou WEBP, com até 5 MB cada.`}
        photos={form.referencePhotos}
        max={MAX_REFERENCE_PHOTOS}
        error={errors.referencePhotos}
        onChange={(referencePhotos) => update({ referencePhotos })}
      />
    </section>
  )
}
