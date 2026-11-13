import { useId } from 'react'
import { TextAreaField } from '../../../../components/TextAreaField'
import { TextField } from '../../../../components/TextField'
import type { PublicCakeCategory, PublicOption, PublicSize } from '../../../../types/public'
import { formatBRL } from '../../../../utils/money'
import { PhotoPicker } from '../PhotoPicker'
import { applyCategory, applySize, cakeLimits, MAX_REFERENCE_PHOTOS, OTHER_SIZES, toggleChoice } from '../orderForm'
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
        {legend} {single ? '(escolha 1)' : `(escolha até ${max})`}
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

/** "1,5 kg", "15 cm · 1,5 kg" — peso e diâmetro, quando cadastrados. */
function measures(size: PublicSize): string | null {
  const parts: string[] = []
  if (size.diameterCm != null) parts.push(`${size.diameterCm} cm`)
  if (size.weightKg != null) parts.push(`${size.weightKg.replace(/\.?0+$/, '').replace('.', ',')} kg`)
  return parts.length ? parts.join(' · ') : null
}

function SizeChoices({ sizes, form, update }: { sizes: PublicSize[] } & Pick<StepProps, 'form' | 'update'>) {
  return (
    <div className={styles.choiceGrid}>
      {sizes.map((option) => (
        <label key={option.id} className={styles.choice}>
          <input type="radio" name="sizeId" checked={form.sizeId === option.id} onChange={() => update(applySize(form, option))} />
          <span className={styles.choiceTitle}>{option.name}</span>
          {measures(option) && !option.name.includes('kg') && <span className={styles.choiceMeta}>{measures(option)}</span>}
          {option.servings != null && <span className={styles.choiceMeta}>{option.servings} fatias</span>}
          <span className={styles.choiceMeta}>{formatBRL(option.basePrice)}</span>
          {option.description && <span className={styles.choiceMeta}>{option.description}</span>}
        </label>
      ))}
    </div>
  )
}

export function StepCake({ form, errors, update, catalog, allowPhotos = true }: StepProps) {
  const titleId = useId()
  const size = catalog.sizes.find((s) => s.id === form.sizeId)
  const categories = catalog.cakeCategories ?? []
  // Tamanhos sem categoria (cadastros antigos) aparecem como "Outros tamanhos".
  const uncategorized = catalog.sizes.filter((s) => s.categoryId == null || !categories.some((c) => c.id === s.categoryId))
  const sizesOf = (c: PublicCakeCategory) => catalog.sizes.filter((s) => s.categoryId === c.id)
  const otherChosen = form.cakeCategoryId === OTHER_SIZES || (!!size && uncategorized.some((s) => s.id === size.id))
  const category = otherChosen ? undefined : categories.find((c) => c.id === (size?.categoryId ?? form.cakeCategoryId))
  const consult = !size && !!category?.consultOnly
  const limits = cakeLimits(size, category)
  const shownSizes = categories.length === 0 ? catalog.sizes : otherChosen ? uncategorized : category ? sizesOf(category) : null

  return (
    <section className={styles.card} aria-labelledby={titleId}>
      <h2 id={titleId}>Seu bolo</h2>

      {categories.length > 0 && (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Tipo de bolo</legend>
          <div className={styles.choiceGrid}>
            {categories.filter((c) => c.consultOnly || sizesOf(c).length > 0).map((option) => (
              <label key={option.id} className={styles.choice}>
                <input
                  type="radio"
                  name="cakeCategoryId"
                  checked={category?.id === option.id}
                  onChange={() => update(applyCategory(form, option, catalog.sizes))}
                />
                <span className={styles.choiceTitle}>{option.name}</span>
                {option.consultOnly && <span className={styles.choiceMeta}>Consulte valores</span>}
              </label>
            ))}
            {uncategorized.length > 0 && (
              <label className={styles.choice}>
                <input
                  type="radio"
                  name="cakeCategoryId"
                  checked={otherChosen}
                  onChange={() => update({ cakeCategoryId: OTHER_SIZES, sizeId: null })}
                />
                <span className={styles.choiceTitle}>Outros tamanhos</span>
              </label>
            )}
          </div>
        </fieldset>
      )}

      {category && (category.description || category.notice) && (
        <div className={styles.notice} role="note">
          {category.description && <p style={{ margin: 0 }}>{category.description}</p>}
          {category.notice && <p style={{ margin: 0 }}><strong>{category.notice}</strong></p>}
        </div>
      )}

      {consult ? (
        <p className={styles.hint}>
          Bolos com 3 andares ou mais são feitos sob consulta. Descreva abaixo o que você imagina e a confeitaria envia
          o valor no orçamento.
        </p>
      ) : shownSizes ? (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Tamanho</legend>
          <SizeChoices sizes={shownSizes} form={form} update={update} />
          <p className={styles.hint}>
            Preço inicial conforme a tabela. O valor final é definido no orçamento, de acordo com a decoração e os
            adicionais.
          </p>
        </fieldset>
      ) : (
        <p className={styles.hint}>Escolha o tipo de bolo para ver os tamanhos e preços.</p>
      )}
      {errors.sizeId && (
        <p className={styles.error} role="alert">
          {errors.sizeId}
        </p>
      )}

      {limits ? (
        <>
          <OptionGroup
            legend="Massa"
            name="flavor"
            options={catalog.flavors}
            selected={form.flavorIds}
            max={limits.maxFlavors}
            error={errors.flavorIds}
            onChange={(flavorIds) => update({ flavorIds })}
          />
          <OptionGroup
            legend="Sabores de recheio"
            name="filling"
            options={catalog.fillings}
            selected={form.fillingIds}
            max={limits.maxFillings}
            error={errors.fillingIds}
            onChange={(fillingIds) => update({ fillingIds })}
          />
        </>
      ) : (
        <p className={styles.hint}>Escolha o tamanho para ver as opções de massa e recheio.</p>
      )}

      <TextAreaField
        label={consult ? 'Descreva o bolo que você imagina' : 'Decoração personalizada'}
        hint={consult
          ? 'Quantos andares, tamanho aproximado, cores, tema e estilo. Se tiver referência, envie a foto abaixo.'
          : 'Descreva cores, tema e estilo. A decoração é sempre personalizada; se tiver uma referência, envie a foto abaixo.'}
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
      {allowPhotos && <PhotoPicker
        label="Fotos de referência (opcional)"
        hint={`Até ${MAX_REFERENCE_PHOTOS} fotos em JPG, PNG ou WEBP, com até 5 MB cada.`}
        photos={form.referencePhotos}
        max={MAX_REFERENCE_PHOTOS}
        error={errors.referencePhotos}
        onChange={(referencePhotos) => update({ referencePhotos })}
      />}
    </section>
  )
}
