import { Link } from 'react-router'
import { useAuth } from '../../hooks/useAuth'
import styles from './forms.module.css'

/** Página inicial do painel. O dashboard com indicadores entra na Fase 11. */
export function AdminHomePage() {
  const { user } = useAuth()

  return (
    <section className={styles.panel}>
      <h1>Olá, {user?.name}!</h1>
      <p>
        Você está no painel administrativo. Em <Link to="/admin/produtos">Produtos</Link> você cadastra tamanhos, massas e
        recheios; em <Link to="/admin/configuracoes">Configurações</Link>, os dados da confeitaria.
      </p>
      <p>Os módulos de agenda, pedidos, orçamentos e financeiro serão adicionados nas próximas etapas.</p>
    </section>
  )
}
