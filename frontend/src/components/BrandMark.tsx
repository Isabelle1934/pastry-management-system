import { LOGO } from '../content/site'
import styles from './BrandMark.module.css'

interface BrandMarkProps {
  name: string
  /** Texto pequeno sob o nome (ex.: "Painel"). */
  caption?: string
  tone?: 'light' | 'dark'
}

/** Logo da confeitaria: a imagem configurada em content/site.ts ou, sem ela, o monograma com a inicial. */
export function BrandMark({ name, caption, tone = 'light' }: BrandMarkProps) {
  const initial = name.trim().charAt(0).toUpperCase() || 'C'
  return (
    <span className={`${styles.brand} ${tone === 'dark' ? styles.dark : ''}`}>
      {LOGO ? (
        <img src={LOGO.src} alt="" className={styles.logo} />
      ) : (
        <span className={styles.monogram} aria-hidden="true">{initial}</span>
      )}
      <span className={styles.text}>
        <span className={styles.name}>{name}</span>
        {caption && <span className={styles.caption}>{caption}</span>}
      </span>
    </span>
  )
}
