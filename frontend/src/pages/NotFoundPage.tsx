import { Link } from 'react-router'
import { PublicLayout } from '../layouts/PublicLayout'
import styles from './public/public.module.css'

export function NotFoundPage() {
  return (
    <PublicLayout>
      <section className={styles.hero}>
        <h1 className={styles.heroTitle}>Página não encontrada</h1>
        <p className={styles.about}>O endereço acessado não existe.</p>
        <Link to="/">Ir para o início</Link>
      </section>
    </PublicLayout>
  )
}
