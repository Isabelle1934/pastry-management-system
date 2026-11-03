import { useCallback } from 'react'
import { Alert } from '../../../components/Alert'
import { useLoader } from '../../../hooks/useLoader'
import { getFinanceSummary } from '../../../services/finance'
import { formatBRL } from '../../../utils/money'
import panel from '../panel.module.css'
import styles from './finance.module.css'
import { Loading } from '../../../components/Loading'

function Stat({ label, value, detail }: { label: string; value: string; detail?: string }) {
  return (
    <div className={styles.stat}>
      <dt>{label}</dt>
      <dd>
        <span className={`${styles.value} ${value.startsWith('-') ? styles.negative : ''}`}>{formatBRL(value)}</span>
        {detail && <span className={styles.detail}>{detail}</span>}
      </dd>
    </div>
  )
}

function plural(count: number, one: string, many: string) {
  return `${count} ${count === 1 ? one : many}`
}

export function SummarySection({ from, to }: { from: string; to: string }) {
  const load = useCallback(() => getFinanceSummary(from, to), [from, to])
  const { data, loading, error } = useLoader(load)

  if (loading && !data) return <Loading />
  if (error) return <Alert tone="error">{error}</Alert>
  if (!data) return null

  return (
    <div className={panel.section}>
      <dl className={styles.stats}>
        <Stat label="Faturamento previsto" value={data.forecastRevenue}
          detail={`${plural(data.forecastOrders, 'pedido confirmado', 'pedidos confirmados')} com retirada no período`} />
        <Stat label="Recebido no período" value={data.received}
          detail={`Sinais ${formatBRL(data.receivedDeposits)} · saldos ${formatBRL(data.receivedBalances)}`
            + (data.refunds !== '0.00' ? ` · reembolsos −${formatBRL(data.refunds)}` : '')} />
        <Stat label="A receber" value={data.pending} detail="Saldo dos pedidos confirmados do período" />
        <Stat label="Despesas" value={data.expenses}
          detail={`Das quais ${formatBRL(data.orderExpenses)} ligadas a pedidos`} />
        <Stat label="Lucro estimado" value={data.estimatedProfit} detail="Previsto − despesas (estimativa)" />
        <Stat label="Resultado de caixa" value={data.cashResult} detail="Recebido − despesas" />
      </dl>

      {data.awaitingDepositOrders > 0 && (
        <Alert tone="info">
          Fora do previsto: {plural(data.awaitingDepositOrders, 'pedido aguarda', 'pedidos aguardam')} o sinal, somando{' '}
          {formatBRL(data.awaitingDepositRevenue)}.
        </Alert>
      )}

      <section className={panel.card} aria-labelledby="by-category-title">
        <h2 id="by-category-title" className={panel.cardTitle}>Despesas por categoria</h2>
        {data.expensesByCategory.length === 0 ? (
          <p className={panel.description}>Nenhuma despesa registrada no período.</p>
        ) : (
          <ul className={styles.categories}>
            {data.expensesByCategory.map((category) => (
              <li key={category.categoryId}>
                <span>{category.name}</span>
                <strong>{formatBRL(category.total)}</strong>
              </li>
            ))}
          </ul>
        )}
      </section>

      <p className={panel.description}>
        Previsto e a receber consideram a data de retirada; recebido, a data em que o pagamento foi confirmado; despesas,
        a data da despesa. Valores sem contabilidade fiscal.
      </p>
    </div>
  )
}
