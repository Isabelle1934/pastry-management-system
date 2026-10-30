import { useApiStatus } from '../hooks/useApiStatus'
import styles from './SystemStatusPage.module.css'

const labels = {
  checking: 'Verificando conexão com o servidor…',
  online: 'Servidor conectado',
  offline: 'Servidor indisponível',
} as const

/** Página temporária da Fase 1: confirma a comunicação frontend ↔ backend. */
export function SystemStatusPage() {
  const status = useApiStatus()

  return (
    <main className={styles.page}>
      <h1>Sistema da Confeitaria</h1>
      <p className={styles.subtitle}>Em construção</p>
      <p className={`${styles.status} ${styles[status]}`} role="status">
        {labels[status]}
      </p>
    </main>
  )
}
