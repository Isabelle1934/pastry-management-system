import { useState } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { TextField } from '../../../components/TextField'
import { changeProduction } from '../../../services/production'
import type { OrderDetail, ProductionStatus } from '../../../types/order'
import { formatBRL, toCents } from '../../../utils/money'
import { PRODUCTION_NEXT, PRODUCTION_PREVIOUS, PRODUCTION_STATUS } from '../../../utils/orderStatus'
import panel from '../panel.module.css'
import { describeSaveError } from '../saveErrors'
import styles from './orders.module.css'

const STEPS: ProductionStatus[] = ['NOT_STARTED', 'IN_PRODUCTION', 'READY', 'DELIVERED']

interface Props {
  order: OrderDetail
  onChanged: () => Promise<void>
}

export function ProductionPanel({ order, onChanged }: Props) {
  const [note, setNote] = useState('')
  const [confirmBalance, setConfirmBalance] = useState(false)
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  const current = order.productionStatus
  const active = order.commercialStatus === 'CONFIRMED'
  const next = active ? PRODUCTION_NEXT[current] : undefined
  const previous = active ? PRODUCTION_PREVIOUS[current] : undefined
  const balanceDue = order.payments.balanceDue
  const delivering = next?.status === 'DELIVERED'
  const hasBalanceDue = balanceDue != null && toCents(balanceDue) > 0

  async function move(status: ProductionStatus) {
    setBusy(true)
    setError(undefined)
    try {
      await changeProduction(order.id, {
        status,
        version: order.version,
        note: note.trim() || undefined,
        confirmBalanceDue: status === 'DELIVERED' ? confirmBalance : undefined,
      })
      setNote('')
      setConfirmBalance(false)
      await onChanged()
    } catch (err) {
      const failure = describeSaveError(err)
      setError(failure.message ?? Object.values(failure.fields)[0] ?? 'Não foi possível atualizar.')
      if (failure.stale) await onChanged()
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className={`${styles.section} ${styles.wide}`} aria-labelledby="production-title">
      <h2 id="production-title">Produção</h2>
      <ol className={styles.steps}>
        {STEPS.map((step, index) => {
          const state = index < STEPS.indexOf(current) ? 'done' : step === current ? 'current' : 'todo'
          return (
            <li key={step} className={styles[state]} aria-current={step === current ? 'step' : undefined}>
              {PRODUCTION_STATUS[step]}
            </li>
          )
        })}
      </ol>

      {!active && current === 'NOT_STARTED' && (
        <p className={styles.muted}>A produção pode ser atualizada depois que o sinal for confirmado.</p>
      )}

      {(next || previous) && (
        <div className={panel.form}>
          {delivering && hasBalanceDue && (
            <>
              <Alert tone="info">
                Ainda falta receber {formatBRL(balanceDue)}. Registre o pagamento em “Pagamentos” ou confirme a entrega
                com saldo em aberto.
              </Alert>
              <label className={styles.check}>
                <input type="checkbox" checked={confirmBalance} onChange={(e) => setConfirmBalance(e.target.checked)} />
                Entregar mesmo com {formatBRL(balanceDue)} em aberto
              </label>
            </>
          )}
          {delivering && (
            <p className={styles.muted}>Ao marcar como entregue, o pedido é concluído e não pode mais ser alterado.</p>
          )}
          <TextField label="Observação (opcional, fica no histórico)" value={note} maxLength={300}
            onChange={(e) => setNote(e.target.value)} />
          {error && <Alert tone="error">{error}</Alert>}
          <div className={panel.actions}>
            {next && (
              <Button loading={busy} disabled={delivering && hasBalanceDue && !confirmBalance}
                onClick={() => void move(next.status)}>
                {next.action}
              </Button>
            )}
            {previous && (
              <Button variant="secondary" disabled={busy} onClick={() => void move(previous)}>
                Voltar para “{PRODUCTION_STATUS[previous]}”
              </Button>
            )}
          </div>
        </div>
      )}
    </section>
  )
}
