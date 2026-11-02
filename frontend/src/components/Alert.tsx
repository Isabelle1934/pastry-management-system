import type { ReactNode } from 'react'
import styles from './Alert.module.css'

interface AlertProps {
  tone: 'error' | 'success' | 'info'
  children: ReactNode
}

export function Alert({ tone, children }: AlertProps) {
  return (
    <div className={`${styles.alert} ${styles[tone]}`} role={tone === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  )
}
