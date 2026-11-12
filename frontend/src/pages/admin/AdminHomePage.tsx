import type { CSSProperties, ReactNode } from 'react'
import { Link } from 'react-router'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { Icon, type IconName } from '../../components/Icon'
import { Loading } from '../../components/Loading'
import { useAuth } from '../../hooks/useAuth'
import { useLoader } from '../../hooks/useLoader'
import { getDashboard } from '../../services/dashboard'
import motion from '../../styles/motion.module.css'
import type { Money } from '../../types/catalog'
import type { PickupItem } from '../../types/order'
import { formatLongDay, formatShortDay } from '../../utils/date'
import { formatBRL, toCents } from '../../utils/money'
import { formatDateTime, PRODUCTION_STATUS } from '../../utils/orderStatus'
import styles from './dashboard.module.css'

function Card({ icon, title, action, className, children }: {
  icon: IconName
  title: string
  action?: { to: string; label: string }
  className?: string
  children: ReactNode
}) {
  return (
    <section className={`${styles.card} ${className ?? ''}`} aria-label={title}>
      <header className={styles.cardHeader}>
        <span className={styles.cardIcon}><Icon name={icon} size={20} /></span>
        <h2>{title}</h2>
        {action && (
          <Link to={action.to} className={styles.cardAction}>
            {action.label} <Icon name="arrowRight" size={16} />
          </Link>
        )}
      </header>
      {children}
    </section>
  )
}

function PickupList({ items, showDay }: { items: PickupItem[]; showDay: boolean }) {
  return (
    <ul className={styles.list}>
      {items.map((item) => (
        <li key={item.id}>
          <Link to={`/admin/pedidos/${item.id}`} className={styles.listLink}>
            <span className={styles.time}>
              {showDay && <small>{formatShortDay(item.pickupDate)}</small>}
              {item.pickupTime}
            </span>
            <span className={styles.listMain}>
              <strong>{item.customerName}</strong>
              <span className={styles.muted}>{item.summary}{item.fulfillmentType === 'DELIVERY' ? ' · delivery' : ''} · {PRODUCTION_STATUS[item.productionStatus]}</span>
            </span>
            {item.balanceDue != null && toCents(item.balanceDue) > 0 && (
              <span className={styles.due}>Falta {formatBRL(item.balanceDue)}</span>
            )}
          </Link>
        </li>
      ))}
    </ul>
  )
}

function Empty({ children }: { children: ReactNode }) {
  return <p className={styles.empty}>{children}</p>
}

/** Valores do mês com barras proporcionais ao maior deles. */
function MonthChart({ rows }: { rows: { label: string; value: Money; tone: string }[] }) {
  const max = Math.max(1, ...rows.map((row) => toCents(row.value)))
  return (
    <dl className={styles.chart}>
      {rows.map((row) => (
        <div key={row.label} className={styles.chartRow}>
          <dt>{row.label}</dt>
          <dd>
            <span className={styles.bar} aria-hidden="true">
              <span className={`${styles.barFill} ${styles[row.tone]}`}
                style={{ '--w': toCents(row.value) / max } as CSSProperties} />
            </span>
            <strong>{formatBRL(row.value)}</strong>
          </dd>
        </div>
      ))}
    </dl>
  )
}

/** Painel inicial: pedidos de hoje, próximas entregas, aprovações pendentes, orçamentos vencendo e o mês. */
export function AdminHomePage() {
  const { user } = useAuth()
  const { data, loading, error, reload } = useLoader(getDashboard)

  const todayPickups = data?.upcomingPickups.filter((item) => item.pickupDate === data.today) ?? []
  const nextPickups = data?.upcomingPickups.filter((item) => item.pickupDate !== data.today) ?? []
  const approvals = data ? data.actions.newOrders + data.actions.proofsToReview + data.actions.awaitingClientResponse : 0
  const forecast = data ? toCents(data.month.forecastRevenue) : 0
  const receivedShare = data && forecast > 0 ? Math.round((toCents(data.month.received) / forecast) * 100) : null

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div>
          {data && <p className={styles.eyebrow}>{formatLongDay(data.today)}</p>}
          <h1>Olá, {user?.name}!</h1>
        </div>
        <Button variant="secondary" onClick={() => void reload()} loading={loading && !!data}>
          <Icon name="refresh" size={18} /> Atualizar
        </Button>
      </header>

      {loading && !data && <Loading />}
      {error && <Alert tone="error">{error}</Alert>}

      {data && (
        <>
          {data.actions.overduePickups > 0 && (
            <Alert tone="error">
              {data.actions.overduePickups === 1
                ? '1 retirada atrasada ainda não foi marcada como entregue.'
                : `${data.actions.overduePickups} retiradas atrasadas ainda não foram marcadas como entregues.`}{' '}
              <Link to="/admin/producao">Ver produção</Link>
            </Alert>
          )}

          <div className={`${styles.grid} ${motion.stagger}`}>
            <Card icon="bag" title="Pedidos de hoje" className={styles.wide}
              action={{ to: '/admin/producao', label: 'Produção' }}>
              <p className={styles.big}>
                {data.production.pickupsToday}
                <span>{data.production.pickupsToday === 1 ? 'retirada hoje' : 'retiradas hoje'}</span>
              </p>
              {todayPickups.length > 0 ? (
                <PickupList items={todayPickups} showDay={false} />
              ) : (
                <Empty>Nenhuma retirada pendente para hoje.</Empty>
              )}
            </Card>

            <Card icon="orders" title="Aguardando aprovação" className={styles.narrow}
              action={{ to: '/admin/pedidos', label: 'Pedidos' }}>
              <p className={styles.big}>
                {approvals}
                <span>aguardando</span>
              </p>
              <ul className={styles.counters}>
                <li>
                  <Link to="/admin/pedidos?situacao=PENDING_REVIEW"
                    className={data.actions.newOrders > 0 ? styles.attention : ''}>
                    <span>Novos para orçar</span><strong>{data.actions.newOrders}</strong>
                  </Link>
                </li>
                <li>
                  <Link to="/admin/pedidos?situacao=AWAITING_DEPOSIT"
                    className={data.actions.proofsToReview > 0 ? styles.attention : ''}>
                    <span>Comprovantes para conferir</span><strong>{data.actions.proofsToReview}</strong>
                  </Link>
                </li>
                <li>
                  <Link to="/admin/pedidos?situacao=QUOTE_SENT">
                    <span>Orçamentos com a cliente</span><strong>{data.actions.awaitingClientResponse}</strong>
                  </Link>
                </li>
              </ul>
            </Card>

            <Card icon="calendar" title="Próximas entregas" className={styles.wide}
              action={{ to: '/admin/producao', label: 'Ver todas' }}>
              {nextPickups.length > 0 ? (
                <PickupList items={nextPickups} showDay />
              ) : (
                <Empty>Nenhuma retirada confirmada nos próximos 7 dias.</Empty>
              )}
            </Card>

            <Card icon="clock" title="Orçamentos perto de vencer" className={styles.narrow}>
              {data.expiringQuotes.length > 0 ? (
                <ul className={styles.list}>
                  {data.expiringQuotes.map((quote) => (
                    <li key={quote.orderId}>
                      <Link to={`/admin/pedidos/${quote.orderId}`} className={styles.listLink}>
                        <span className={styles.listMain}>
                          <strong>{quote.customerName}</strong>
                          <span className={styles.muted}>vence {formatDateTime(quote.validUntil)}</span>
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : (
                <Empty>Nenhum orçamento vencendo nos próximos dias.</Empty>
              )}
            </Card>

            <Card icon="finance" title="Resumo financeiro do mês" className={styles.full}
              action={{ to: '/admin/financeiro', label: 'Financeiro' }}>
              <div className={styles.finance}>
                <div className={styles.financeHighlight}>
                  <span className={styles.muted}>Recebido</span>
                  <strong>{formatBRL(data.month.received)}</strong>
                  {receivedShare != null && (
                    <span className={styles.muted}>{receivedShare}% do faturamento previsto</span>
                  )}
                </div>
                <MonthChart rows={[
                  { label: 'Faturamento previsto', value: data.month.forecastRevenue, tone: 'forecast' },
                  { label: 'Recebido', value: data.month.received, tone: 'received' },
                  { label: 'A receber', value: data.month.pending, tone: 'pending' },
                  { label: 'Despesas', value: data.month.expenses, tone: 'expenses' },
                ]} />
              </div>
            </Card>
          </div>
        </>
      )}
    </div>
  )
}
