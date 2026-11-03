import type { ReactNode } from 'react'
import { Icon } from './Icon'
import styles from './Alert.module.css'

interface AlertProps {
  tone: 'error' | 'success' | 'info'
  children: ReactNode
}

const ICONS = { error: 'alert', success: 'check', info: 'info' } as const

export function Alert({ tone, children }: AlertProps) {
  return (
    <div className={`${styles.alert} ${styles[tone]}`} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon name={ICONS[tone]} size={18} className={styles.icon} />
      <div className={styles.body}>{children}</div>
    </div>
  )
}
