import { useEffect, useId, useMemo, useRef } from 'react'
import { Button } from '../../../components/Button'
import { ACCEPTED_IMAGE_TYPES } from './orderForm'
import styles from './order.module.css'

interface PhotoPickerProps {
  label: string
  hint: string
  photos: File[]
  max: number
  error?: string
  onChange: (photos: File[]) => void
}

/** Seleção de fotos com pré-visualização local (nada é enviado até a revisão final). */
export function PhotoPicker({ label, hint, photos, max, error, onChange }: PhotoPickerProps) {
  const inputId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const previews = useMemo(() => photos.map((photo) => URL.createObjectURL(photo)), [photos])

  useEffect(() => () => previews.forEach((url) => URL.revokeObjectURL(url)), [previews])

  function addFiles(list: FileList | null) {
    if (!list) return
    onChange([...photos, ...Array.from(list)].slice(0, max))
    if (inputRef.current) inputRef.current.value = ''
  }

  return (
    <div className={styles.fieldset}>
      <label htmlFor={inputId} className={styles.legend}>
        {label}
      </label>
      <p className={styles.hint}>{hint}</p>
      {photos.length > 0 && (
        <div className={styles.photoGrid}>
          {photos.map((photo, index) => (
            <div key={`${photo.name}-${index}`} className={styles.photo}>
              <img src={previews[index]} alt={`Foto escolhida: ${photo.name}`} />
              <button
                type="button"
                className={styles.removePhoto}
                aria-label={`Remover ${photo.name}`}
                onClick={() => onChange(photos.filter((_, i) => i !== index))}
              >
                ×
              </button>
            </div>
          ))}
        </div>
      )}
      <input
        ref={inputRef}
        id={inputId}
        type="file"
        accept={ACCEPTED_IMAGE_TYPES.join(',')}
        multiple={max > 1}
        hidden
        onChange={(e) => addFiles(e.target.files)}
      />
      {photos.length < max && (
        <Button variant="secondary" className={styles.fileButton} onClick={() => inputRef.current?.click()}>
          {photos.length === 0 ? 'Escolher foto' : 'Adicionar outra foto'}
        </Button>
      )}
      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
