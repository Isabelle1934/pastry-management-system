import type { SitePhoto } from '../content/site'
import { Icon } from './Icon'
import styles from './PhotoSlot.module.css'

interface PhotoSlotProps {
  photo: SitePhoto | null
  /** O que deve aparecer neste espaço quando a foto real chegar. */
  placeholder: string
  className?: string
}

/** Foto real da confeitaria ou um espaço temporário, claramente identificado, enquanto ela não for enviada. */
export function PhotoSlot({ photo, placeholder, className }: PhotoSlotProps) {
  if (photo) {
    return (
      <figure className={`${styles.slot} ${className ?? ''}`}>
        <img src={photo.src} alt={photo.alt} loading="lazy" decoding="async" />
      </figure>
    )
  }
  return (
    <figure className={`${styles.slot} ${styles.placeholder} ${className ?? ''}`}>
      <Icon name="image" size={28} />
      <figcaption>
        <strong>Espaço temporário</strong>
        <span>{placeholder}</span>
      </figcaption>
    </figure>
  )
}
