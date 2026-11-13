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
        <Stat label="A receber" value={data.pending} detail="Saldo dos pedidos confirmados do período" />
        <Stat label="Recebido no período" value={data.received}
          detail={`Sinais ${formatBRL(data.receivedDeposits)} · saldos ${formatBRL(data.receivedBalances)}`
            + (data.refunds !== '0.00' ? ` · reembolsos −${formatBRL(data.refunds)}` : '')} />
        <Stat label="Lucro" value={data.profit} detail="Recebido no período, já descontados os reembolsos" />
        <Stat label="Faturamento previsto" value={data.forecastRevenue}
          detail={`${plural(data.forecastOrders, 'pedido confirmado', 'pedidos confirmados')} com data no período`} />
      </dl>

      {data.awaitingDepositOrders > 0 && (
        <Alert tone="info">
          Fora do previsto: {plural(data.awaitingDepositOrders, 'pedido aguarda', 'pedidos aguardam')} o sinal, somando{' '}
          {formatBRL(data.awaitingDepositRevenue)}.
        </Alert>
      )}

      <p className={panel.description}>
        Previsto e a receber consideram a data agendada (retirada ou entrega); recebido, a data em que a confeitaria
        confirmou o pagamento — comprovantes ainda em análise não entram. Valores sem contabilidade fiscal.
      </p>
    </div>
  )
}
