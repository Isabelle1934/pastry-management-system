import { useCallback, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { useLoader } from '../../hooks/useLoader'
import { changeProduction, getPickupBoard } from '../../services/production'
import type { PickupItem, ProductionStatus } from '../../types/order'
import { formatLongDay } from '../../utils/date'
import { formatBRL, toCents } from '../../utils/money'
import { PRODUCTION_NEXT, PRODUCTION_STATUS, type Tone } from '../../utils/orderStatus'
import { groupPickups } from '../../utils/pickups'
import styles from './orders/orders.module.css'
import { StatusChip } from './orders/StatusChip'
import panel from './panel.module.css'
import { describeSaveError } from './saveErrors'

const PERIODS = [7, 14, 30]
const TONE: Record<ProductionStatus, Tone> = { NOT_STARTED: 'neutral', IN_PRODUCTION: 'info', READY: 'ok', DELIVERED: 'ok' }

function QuickAction({ item, onDone }: { item: PickupItem; onDone: () => Promise<void> }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const next = PRODUCTION_NEXT[item.productionStatus]
  if (!next) return null
  const balanceDue = item.balanceDue != null && toCents(item.balanceDue) > 0

  if (next.status === 'DELIVERED' && balanceDue) {
    return (
      <Link to={`/admin/pedidos/${item.id}`} className={styles.filter}
        style={{ display: 'inline-flex', alignItems: 'center', textDecoration: 'none' }}>
        Receber saldo e entregar
      </Link>
    )
  }

  async function run() {
    if (!next) return
    setBusy(true)
    setError(undefined)
    try {
      await changeProduction(item.id, { status: next.status, version: item.version })
      await onDone()
    } catch (err) {
      const failure = describeSaveError(err)
      setError(failure.message ?? 'Não foi possível atualizar.')
      if (failure.stale) await onDone()
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <Button variant="secondary" loading={busy} onClick={() => void run()}>{next.action}</Button>
      {error && <Alert tone="error">{error}</Alert>}
    </>
  )
}

export function ProductionPage() {
  const [params, setParams] = useSearchParams()
  const days = PERIODS.includes(Number(params.get('dias'))) ? Number(params.get('dias')) : 14
  const load = useCallback(() => getPickupBoard(days), [days])
  const { data, loading, error, reload } = useLoader(load)

  const refresh = useCallback(async () => {
    await reload()
  }, [reload])

  const counts = (data?.orders ?? []).reduce<Partial<Record<ProductionStatus, number>>>((acc, item) => {
    acc[item.productionStatus] = (acc[item.productionStatus] ?? 0) + 1
    return acc
  }, {})

  return (
    <div className={panel.page}>
      <h1>Produção e retiradas</h1>
      <p className={panel.intro}>
        Pedidos confirmados com retirada nos próximos dias, incluindo os que já passaram da data sem entrega.
      </p>

      <div className={styles.filters} role="group" aria-label="Período">
        {PERIODS.map((period) => (
          <button key={period} type="button" className={styles.filter} aria-pressed={period === days}
            onClick={() => setParams({ dias: String(period) })}>
            Próximos {period} dias
          </button>
        ))}
      </div>

      {data && data.orders.length > 0 && (
        <p className={styles.muted}>
          {(['NOT_STARTED', 'IN_PRODUCTION', 'READY'] as ProductionStatus[])
            .map((status) => `${PRODUCTION_STATUS[status]}: ${counts[status] ?? 0}`).join(' · ')}
        </p>
      )}

      {loading && !data && <p role="status">Carregando…</p>}
      {error && <Alert tone="error">{error}</Alert>}
      {data && data.orders.length === 0 && (
        <p className={panel.empty}>Nenhuma retirada confirmada até {formatLongDay(data.until)}.</p>
      )}

      {data && groupPickups(data.orders).map((group) => {
        const title = group.date ? formatLongDay(group.date) : 'Atrasados (retirada já passou)'
        return (
          <section key={group.date ?? 'overdue'} aria-label={title} className={panel.section}>
            <h2 className={`${styles.dayTitle} ${group.date ? '' : styles.overdue}`}>{title}</h2>
            <ul className={panel.list}>
              {group.items.map((item) => (
                <li key={item.id} className={styles.orderLink}>
                  <div className={styles.row}>
                    <Link to={`/admin/pedidos/${item.id}`} className={styles.name}>
                      {item.pickupTime} · {item.customerName}
                    </Link>
                    <StatusChip label={PRODUCTION_STATUS[item.productionStatus]} tone={TONE[item.productionStatus]} />
                  </div>
                  <div className={styles.row}>
                    <span>
                      {!group.date && `${formatLongDay(item.pickupDate)} · `}
                      {item.sizeName}
                      {item.cakeText && ` · “${item.cakeText}”`}
                      {item.topperRequested && ' · com topo'}
                    </span>
                    {item.balanceDue != null && toCents(item.balanceDue) > 0 ? (
                      <strong>Falta receber {formatBRL(item.balanceDue)}</strong>
                    ) : (
                      <span>Pago</span>
                    )}
                  </div>
                  <div className={panel.actions}>
                    <QuickAction item={item} onDone={refresh} />
                    <a href={item.whatsappLink} target="_blank" rel="noopener noreferrer">
                      WhatsApp {item.customerWhatsapp}
                    </a>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        )
      })}
    </div>
  )
}
