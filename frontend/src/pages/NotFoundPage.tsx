import { Link } from 'react-router'
import styles from './SystemStatusPage.module.css'

export function NotFoundPage() {
  return (
    <main className={styles.page}>
      <h1>Página não encontrada</h1>
      <p className={styles.subtitle}>O endereço acessado não existe.</p>
      <p>
        <Link to="/">Ir para o início</Link>
      </p>
    </main>
  )
}
