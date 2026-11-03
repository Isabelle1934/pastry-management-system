import { Link } from 'react-router'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { useAuth } from '../../hooks/useAuth'
import { useLoader } from '../../hooks/useLoader'
import { getDashboard } from '../../services/dashboard'
import { formatShortDay } from '../../utils/date'
import { formatBRL, toCents } from '../../utils/money'
import { formatDateTime, PRODUCTION_STATUS } from '../../utils/orderStatus'
import styles from './dashboard.module.css'
import panel from './panel.module.css'
import { Loading } from '../../components/Loading'

function Tile({ to, count, label, attention = false }: { to: string; count: number; label: string; attention?: boolean }) {
  return (
    <Link to={to} className={`${styles.tile} ${attention && count > 0 ? styles.attention : ''}`}>
      <span className={styles.count}>{count}</span>
      <span className={styles.label}>{label}</span>
    </Link>
  )
}

/** Painel inicial: o que pede ação agora, a produção, as próximas retiradas e os números do mês. */
export function AdminHomePage() {
  const { user } = useAuth()
  const { data, loading, error, reload } = useLoader(getDashboard)

  return (
    <div className={panel.page}>
      <div className={panel.toolbar}>
        <h1>Olá, {user?.name}!</h1>
        <Button variant="secondary" onClick={() => void reload()} loading={loading && !!data}>Atualizar</Button>
      </div>

      {loading && !data && <Loading />}
      {error && <Alert tone="error">{error}</Alert>}

      {data && (
        <>
          <section aria-labelledby="actions-title" className={panel.section}>
            <h2 id="actions-title" className={panel.cardTitle}>Precisa da sua atenção</h2>
            <div className={styles.grid}>
              <Tile to="/admin/pedidos?situacao=PENDING_REVIEW" count={data.actions.newOrders}
                label="Pedidos novos para orçar" attention />
              <Tile to="/admin/pedidos?situacao=AWAITING_DEPOSIT" count={data.actions.proofsToReview}
                label="Comprovantes para conferir" attention />
              <Tile to="/admin/producao" count={data.actions.overduePickups} label="Retiradas atrasadas" attention />
              <Tile to="/admin/pedidos?situacao=QUOTE_SENT" count={data.actions.awaitingClientResponse}
                label="Orçamentos aguardando a cliente" />
              <Tile to="/admin/pedidos?situacao=AWAITING_DEPOSIT" count={data.actions.awaitingDeposit}
                label="Aguardando sinal" />
              <Tile to="/admin/avisos" count={data.unreadNotifications} label="Avisos não lidos" />
            </div>
          </section>

          <section aria-labelledby="production-title" className={panel.section}>
            <h2 id="production-title" className={panel.cardTitle}>Produção</h2>
            <div className={styles.grid}>
              <Tile to="/admin/producao" count={data.production.pickupsToday} label="Retiradas hoje" />
              <Tile to="/admin/producao" count={data.production.notStarted} label="Confirmados, não iniciados" />
              <Tile to="/admin/producao" count={data.production.inProduction} label="Em produção" />
              <Tile to="/admin/producao" count={data.production.ready} label="Prontos para retirada" />
            </div>
          </section>

          <div className={styles.columns}>
            <section aria-labelledby="pickups-title" className={styles.block}>
              <h2 id="pickups-title">Próximas retiradas</h2>
              {data.upcomingPickups.length === 0 ? (
                <p className={styles.muted}>Nenhuma retirada confirmada nos próximos 7 dias.</p>
              ) : (
                <ul className={styles.rows}>
                  {data.upcomingPickups.map((item) => (
                    <li key={item.id}>
                      <span>
                        <Link to={`/admin/pedidos/${item.id}`}>{item.customerName}</Link>
                        <br />
                        <span className={styles.muted}>
                          {formatShortDay(item.pickupDate)} às {item.pickupTime} · {PRODUCTION_STATUS[item.productionStatus]}
                        </span>
                      </span>
                      {item.balanceDue != null && toCents(item.balanceDue) > 0 && (
                        <span>Falta {formatBRL(item.balanceDue)}</span>
                      )}
                    </li>
                  ))}
                </ul>
              )}
              <p><Link to="/admin/producao">Ver produção e retiradas</Link></p>
            </section>

            <section aria-labelledby="month-title" className={styles.block}>
              <h2 id="month-title">Este mês</h2>
              <ul className={styles.rows}>
                <li><span>Faturamento previsto</span><strong>{formatBRL(data.month.forecastRevenue)}</strong></li>
                <li><span>Recebido</span><strong>{formatBRL(data.month.received)}</strong></li>
                <li><span>A receber</span><strong>{formatBRL(data.month.pending)}</strong></li>
                <li><span>Despesas</span><strong>{formatBRL(data.month.expenses)}</strong></li>
              </ul>
              <p><Link to="/admin/financeiro">Ver financeiro</Link></p>
            </section>
          </div>

          {data.expiringQuotes.length > 0 && (
            <section aria-labelledby="expiring-title" className={styles.block}>
              <h2 id="expiring-title">Orçamentos perto de vencer</h2>
              <ul className={styles.rows}>
                {data.expiringQuotes.map((quote) => (
                  <li key={quote.orderId}>
                    <Link to={`/admin/pedidos/${quote.orderId}`}>{quote.customerName}</Link>
                    <span>vence {formatDateTime(quote.validUntil)}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
    </div>
  )
}
