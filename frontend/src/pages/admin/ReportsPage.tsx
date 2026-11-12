import { useCallback } from 'react'
import { useSearchParams } from 'react-router'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { Loading } from '../../components/Loading'
import { useLoader } from '../../hooks/useLoader'
import { getReport, type Ranked } from '../../services/reports'
import type { CommercialStatus } from '../../types/order'
import { formatBRL } from '../../utils/money'
import { COMMERCIAL_STATUS } from '../../utils/orderStatus'
import { currentMonth, formatMonth, isMonthKey, monthRange, shiftMonth } from '../../utils/period'
import finance from './finance/finance.module.css'
import styles from './catalog/catalog.module.css'
import panel from './panel.module.css'

function Ranking({ title, rows, unit, empty }: { title: string; rows: Ranked[]; unit: string; empty: string }) {
  const max = Math.max(1, ...rows.map((r) => r.quantity))
  return (
    <section className={panel.card} aria-label={title}>
      <h2 className={panel.cardTitle}>{title}</h2>
      {rows.length === 0 ? (
        <p className={panel.description}>{empty}</p>
      ) : (
        <ol className={styles.ranking}>
          {rows.map((row) => (
            <li key={`${row.name}-${row.group ?? ''}`} className={styles.rankRow}>
              <span>
                {row.name}
                {row.group && <span className={panel.description}> · {row.group}</span>}
              </span>
              <strong>{row.quantity} {unit}</strong>
              <span className={styles.rankBar} aria-hidden="true">
                <span style={{ width: `${(row.quantity / max) * 100}%` }} />
              </span>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

/** Relatórios do mês pela data agendada: vendas, produtos mais pedidos, bolos e atendimento. */
export function ReportsPage() {
  const [params, setParams] = useSearchParams()
  const month = isMonthKey(params.get('mes')) ? params.get('mes')! : currentMonth()
  const { from, to } = monthRange(month)
  const load = useCallback(() => getReport(from, to), [from, to])
  const { data, loading, error } = useLoader(load)

  const statusRows = data
    ? (Object.entries(data.statusCounts) as [CommercialStatus, number][]).filter(([, count]) => count > 0)
    : []

  return (
    <div className={panel.page}>
      <h1>Relatórios</h1>
      <p className={panel.intro}>
        Pedidos com data de retirada ou entrega no mês. Produtos, bolos e sabores consideram só pedidos confirmados
        ou concluídos, com os nomes como estavam na encomenda.
      </p>
      <div className={finance.monthNav}>
        <Button variant="secondary" onClick={() => setParams({ mes: shiftMonth(month, -1) })} aria-label="Mês anterior">‹</Button>
        <h2 className={finance.monthTitle} aria-live="polite">{formatMonth(month)}</h2>
        <Button variant="secondary" onClick={() => setParams({ mes: shiftMonth(month, 1) })} aria-label="Próximo mês">›</Button>
        {month !== currentMonth() && (
          <Button variant="secondary" onClick={() => setParams({})}>Mês atual</Button>
        )}
      </div>

      {loading && <Loading />}
      {error && <Alert tone="error">{error}</Alert>}
      {data && (
        <>
          <div className={styles.kpis}>
            <div className={styles.kpi}><span>Pedidos no mês</span><strong>{data.totals.orders}</strong></div>
            <div className={styles.kpi}><span>Vendidos</span><strong>{data.totals.sold}</strong></div>
            <div className={styles.kpi}><span>Não fechados</span><strong>{data.totals.lost}</strong></div>
            <div className={styles.kpi}><span>Faturamento (vendidos)</span><strong>{formatBRL(data.totals.soldRevenue)}</strong></div>
            <div className={styles.kpi}><span>Ticket médio</span><strong>{formatBRL(data.totals.averageTicket)}</strong></div>
            <div className={styles.kpi}><span>Recebido no mês</span><strong>{formatBRL(data.totals.received)}</strong></div>
          </div>

          <div className={styles.reportGrid}>
            <Ranking title="Produtos mais pedidos" rows={data.topProducts} unit="un."
              empty="Nenhum produto do catálogo vendido no mês." />
            <Ranking title="Categorias" rows={data.categories} unit="un." empty="Sem vendas de produtos no mês." />
            <Ranking title="Bolos por tamanho" rows={data.cakeSizes} unit="" empty="Nenhum bolo personalizado vendido no mês." />
            <Ranking title="Massas preferidas" rows={data.flavors} unit="" empty="Sem dados no mês." />
            <Ranking title="Recheios preferidos" rows={data.fillings} unit="" empty="Sem dados no mês." />
            <section className={panel.card} aria-label="Atendimento e situação">
              <h2 className={panel.cardTitle}>Atendimento</h2>
              <ul className={panel.meta}>
                <li>Retirada: {data.fulfillment.pickup}</li>
                <li>Delivery: {data.fulfillment.delivery}</li>
                <li>Bolos personalizados: {data.customCakes}</li>
                <li>Com topo de bolo: {data.toppers}</li>
              </ul>
              <h2 className={panel.cardTitle}>Situação dos pedidos</h2>
              {statusRows.length === 0 ? (
                <p className={panel.description}>Nenhum pedido no mês.</p>
              ) : (
                <ul className={panel.meta}>
                  {statusRows.map(([status, count]) => (
                    <li key={status}>{COMMERCIAL_STATUS[status].label}: {count}</li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  )
}
