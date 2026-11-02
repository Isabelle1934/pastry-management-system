import type { Tone } from '../../../utils/orderStatus'
import styles from './orders.module.css'

export function StatusChip({ label, tone }: { label: string; tone: Tone }) {
  const toneClass = tone === 'ok' ? styles.ok : tone === 'warn' ? styles.warn : tone === 'info' ? styles.info : ''
  return <span className={`${styles.chip} ${toneClass}`}>{label}</span>
}
