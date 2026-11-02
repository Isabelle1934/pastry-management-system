import { useAuth } from '../../hooks/useAuth'
import styles from './forms.module.css'

/** Página inicial do painel. O dashboard com indicadores entra na Fase 11. */
export function AdminHomePage() {
  const { user } = useAuth()

  return (
    <section className={styles.panel}>
      <h1>Olá, {user?.name}!</h1>
      <p>
        Você está no painel administrativo. Os módulos de pedidos, agenda, orçamentos e financeiro serão adicionados
        nas próximas etapas do desenvolvimento.
      </p>
    </section>
  )
}
