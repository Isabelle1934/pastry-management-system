import { useState, type FormEvent } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { SelectField } from '../../../components/SelectField'
import { TextField } from '../../../components/TextField'
import { confirmPayment, orderFileUrl, registerPayment, rejectPayment } from '../../../services/orders'
import type { OrderDetail, PaymentKind, PaymentMethod, PaymentRecord } from '../../../types/order'
import { formatBRL, parseMoneyInput, toCents } from '../../../utils/money'
import { formatDateTime, PAYMENT_STATUS } from '../../../utils/orderStatus'
import panel from '../panel.module.css'
import { describeSaveError } from '../saveErrors'
import styles from './orders.module.css'
import { StatusChip } from './StatusChip'

const KIND_LABEL: Record<PaymentKind, string> = { DEPOSIT: 'Sinal', BALANCE: 'Saldo', REFUND: 'Reembolso' }
const METHOD_LABEL: Record<PaymentMethod, string> = { PIX: 'Pix', CASH: 'Dinheiro' }
const RECORD_STATUS = {
  PENDING_REVIEW: { label: 'Aguardando conferência', tone: 'info' as const },
  CONFIRMED: { label: 'Confirmado', tone: 'ok' as const },
  REJECTED: { label: 'Não aceito', tone: 'warn' as const },
}

interface Props {
  order: OrderDetail
  onChanged: () => Promise<void>
}

function PendingReview({ order, payment, onChanged }: Props & { payment: PaymentRecord }) {
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [acceptAsDeposit, setAcceptAsDeposit] = useState(false)
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const deposit = order.payments.depositAmount
  const belowDeposit =
    deposit != null && toCents(order.payments.received) + toCents(payment.amount) < toCents(deposit)

  async function run(action: () => Promise<unknown>) {
    setBusy(true)
    setError(undefined)
    try {
      await action()
      await onChanged()
    } catch (err) {
      const failure = describeSaveError(err)
      setError(failure.message ?? Object.values(failure.fields)[0] ?? 'Não foi possível concluir.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={panel.formCard}>
      <h3>Comprovante para conferir: {formatBRL(payment.amount)}</h3>
      <div className={panel.form}>
        {payment.proofFileId && (
          <p>
            <a href={orderFileUrl(order.id, payment.proofFileId)} target="_blank" rel="noreferrer">
              Abrir comprovante enviado pela cliente
            </a>
          </p>
        )}
        <p className={styles.muted}>
          Confira se o valor entrou na sua conta antes de confirmar. Ao confirmar o sinal, o pedido é confirmado e o
          horário fica reservado.
        </p>
        {belowDeposit && (
          <label className={styles.check}>
            <input type="checkbox" checked={acceptAsDeposit} onChange={(e) => setAcceptAsDeposit(e.target.checked)} />
            O valor é menor que o sinal ({formatBRL(deposit)}). Confirmar o pedido mesmo assim.
          </label>
        )}
        {error && <Alert tone="error">{error}</Alert>}
        {rejecting ? (
          <>
            <TextField label="Motivo (a cliente verá esta mensagem)" value={reason} maxLength={300} autoFocus
              onChange={(e) => setReason(e.target.value)} />
            <div className={panel.actions}>
              <Button loading={busy} disabled={!reason.trim()}
                onClick={() => void run(() => rejectPayment(payment.id, reason.trim()))}>
                Confirmar que não foi aceito
              </Button>
              <Button variant="secondary" onClick={() => setRejecting(false)} disabled={busy}>Voltar</Button>
            </div>
          </>
        ) : (
          <div className={panel.actions}>
            <Button loading={busy} onClick={() => void run(() => confirmPayment(payment.id, acceptAsDeposit))}>
              Confirmar recebimento
            </Button>
            <Button variant="secondary" onClick={() => setRejecting(true)} disabled={busy}>Não aceitar</Button>
          </div>
        )}
      </div>
    </div>
  )
}

function RegisterForm({ order, onChanged, onClose }: Props & { onClose: () => void }) {
  const confirmed = order.commercialStatus === 'CONFIRMED' || order.commercialStatus === 'COMPLETED'
  const [kind, setKind] = useState<PaymentKind>(confirmed ? 'BALANCE' : 'DEPOSIT')
  const [method, setMethod] = useState<PaymentMethod>('PIX')
  const [amount, setAmount] = useState(() => {
    const suggestion = confirmed ? order.payments.balanceDue : order.payments.depositAmount
    return suggestion && suggestion !== '0.00' ? suggestion.replace('.', ',') : ''
  })
  const [notes, setNotes] = useState('')
  const [acceptAsDeposit, setAcceptAsDeposit] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string>()
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const value = parseMoneyInput(amount)
    if (!value || value === '0.00') {
      setErrors({ amount: 'Informe o valor recebido.' })
      return
    }
    setErrors({})
    setFormError(undefined)
    setSaving(true)
    try {
      await registerPayment(order.id, { kind, method, amount: value, notes: notes.trim(), acceptAsDeposit })
      await onChanged()
      onClose()
    } catch (err) {
      const failure = describeSaveError(err)
      setErrors(failure.fields)
      setFormError(failure.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className={panel.formCard}>
      <h3>Registrar pagamento recebido</h3>
      <form className={panel.form} onSubmit={handleSubmit} noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}
        <div className={panel.row}>
          <SelectField label="Tipo" value={kind} onChange={(e) => setKind(e.target.value as PaymentKind)}
            options={(['DEPOSIT', 'BALANCE', 'REFUND'] as PaymentKind[]).map((k) => ({ value: k, label: KIND_LABEL[k] }))} />
          <SelectField label="Forma" value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}
            options={(['PIX', 'CASH'] as PaymentMethod[]).map((m) => ({ value: m, label: METHOD_LABEL[m] }))} />
        </div>
        <TextField label="Valor (R$)" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)}
          error={errors.amount} placeholder="0,00" />
        <TextField label="Observação (opcional)" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={300} />
        {kind === 'DEPOSIT' && order.commercialStatus === 'AWAITING_DEPOSIT' && (
          <label className={styles.check}>
            <input type="checkbox" checked={acceptAsDeposit} onChange={(e) => setAcceptAsDeposit(e.target.checked)} />
            Se o valor for menor que o sinal, confirmar o pedido mesmo assim
          </label>
        )}
        {kind === 'REFUND' && (
          <p className={styles.muted}>O reembolso é só um registro: a devolução do dinheiro é feita por você.</p>
        )}
        <div className={panel.actions}>
          <Button type="submit" loading={saving}>Registrar</Button>
          <Button variant="secondary" onClick={onClose} disabled={saving}>Cancelar</Button>
        </div>
      </form>
    </div>
  )
}

export function PaymentsPanel({ order, onChanged }: Props) {
  const [registering, setRegistering] = useState(false)
  const summary = order.payments
  const pending = summary.payments.filter((p) => p.status === 'PENDING_REVIEW')

  return (
    <section className={`${styles.section} ${styles.wide}`} aria-labelledby="payments-title">
      <div className={styles.row}>
        <h2 id="payments-title">Pagamentos</h2>
        {summary.total && !registering && (
          <Button variant="secondary" onClick={() => setRegistering(true)}>Registrar pagamento</Button>
        )}
      </div>

      {summary.total ? (
        <dl className={styles.dl}>
          <dt>Total do orçamento</dt><dd>{formatBRL(summary.total)}</dd>
          <dt>Sinal (50%)</dt><dd>{formatBRL(summary.depositAmount)}</dd>
          <dt>Recebido</dt><dd>{formatBRL(summary.received)}</dd>
          <dt>Falta receber</dt><dd><strong>{formatBRL(summary.balanceDue)}</strong></dd>
          <dt>Situação</dt><dd>{PAYMENT_STATUS[summary.status]}</dd>
        </dl>
      ) : (
        <p className={styles.muted}>Os pagamentos ficam disponíveis depois que a cliente aceitar o orçamento.</p>
      )}

      {pending.map((payment) => (
        <PendingReview key={payment.id} order={order} payment={payment} onChanged={onChanged} />
      ))}

      {registering && <RegisterForm order={order} onChanged={onChanged} onClose={() => setRegistering(false)} />}

      {summary.payments.length > 0 && (
        <ul className={styles.history}>
          {[...summary.payments].reverse().map((payment) => (
            <li key={payment.id}>
              <div className={styles.row}>
                <strong>
                  {KIND_LABEL[payment.kind]} · {formatBRL(payment.amount)} · {METHOD_LABEL[payment.method]}
                </strong>
                <StatusChip {...RECORD_STATUS[payment.status]} />
              </div>
              <p className={styles.muted}>
                {formatDateTime(payment.createdAt)} · {payment.submittedBy === 'CLIENT' ? 'Enviado pela cliente' : 'Registrado por você'}
                {payment.notes && ` · ${payment.notes}`}
                {payment.reviewNote && ` · ${payment.reviewNote}`}
                {payment.proofFileId && (
                  <>
                    {' · '}
                    <a href={orderFileUrl(order.id, payment.proofFileId)} target="_blank" rel="noreferrer">comprovante</a>
                  </>
                )}
              </p>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
