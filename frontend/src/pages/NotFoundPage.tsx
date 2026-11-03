import { Link } from 'react-router'
import { PublicLayout } from '../layouts/PublicLayout'
import styles from './public/public.module.css'

export function NotFoundPage() {
  return (
    <PublicLayout>
      <section className={styles.notFound}>
        <p className={styles.eyebrow}>Erro 404</p>
        <h1>Página não encontrada</h1>
        <p>O endereço acessado não existe.</p>
        <Link to="/" className={styles.cta}>Ir para o início</Link>
      </section>
    </PublicLayout>
  )
}
