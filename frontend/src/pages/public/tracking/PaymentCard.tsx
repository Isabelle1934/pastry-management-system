import { useRef, useState } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { ApiError } from '../../../services/api'
import { submitDepositProof, trackingFileUrl } from '../../../services/tracking'
import type { TrackingView } from '../../../types/order'
import { formatBRL } from '../../../utils/money'
import { formatDateTime } from '../../../utils/orderStatus'
import styles from '../order/order.module.css'

const ACCEPTED = 'image/jpeg,image/png,application/pdf'
const MAX_BYTES = 5 * 1024 * 1024
const STATUS = {
  PENDING_REVIEW: 'Em conferência pela confeitaria',
  CONFIRMED: 'Confirmado',
  REJECTED: 'Não aceito',
} as const
const KIND = { DEPOSIT: 'Sinal', BALANCE: 'Saldo', REFUND: 'Reembolso' } as const

export function PaymentCard({ view, onChange }: { view: TrackingView; onChange: (view: TrackingView) => void }) {
  const payment = view.payment!
  const inputRef = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string>()
  const [sending, setSending] = useState(false)
  const [copied, setCopied] = useState(false)
  const [method, setMethod] = useState<'PIX' | 'CARD_LINK'>('PIX')
  const awaitingDeposit = view.commercialStatus === 'AWAITING_DEPOSIT'
  const canPayByCard = awaitingDeposit && !!payment.cardPaymentLink

  async function copyPix() {
    if (!payment.pixKey) return
    try {
      await navigator.clipboard.writeText(payment.pixKey)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  async function upload(files: FileList | null) {
    const file = files?.[0]
    if (inputRef.current) inputRef.current.value = ''
    if (!file) return
    const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
    if (!['jpg', 'jpeg', 'png', 'pdf'].includes(extension)) {
      setError('Envie o comprovante em JPG, PNG ou PDF.')
      return
    }
    if (file.size > MAX_BYTES) {
      setError('O comprovante deve ter no máximo 5 MB.')
      return
    }
    setError(undefined)
    setSending(true)
    try {
      onChange(await submitDepositProof(file, canPayByCard ? method : 'PIX'))
    } catch (err) {
      setError(err instanceof ApiError ? (Object.values(err.fieldErrors)[0] ?? err.message) : 'Não foi possível enviar.')
    } finally {
      setSending(false)
    }
  }

  return (
    <section className={styles.card} aria-labelledby="payment-title">
      <h2 id="payment-title">Pagamento</h2>
      <dl className={styles.reviewList}>
        <dt>Total</dt><dd style={{ textAlign: 'right' }}>{formatBRL(payment.total)}</dd>
        <dt>Sinal (50%)</dt><dd style={{ textAlign: 'right' }}>{formatBRL(payment.depositAmount)}</dd>
        <dt>Recebido</dt><dd style={{ textAlign: 'right' }}>{formatBRL(payment.received)}</dd>
        <dt><strong>Falta pagar</strong></dt><dd style={{ textAlign: 'right' }}><strong>{formatBRL(payment.balanceDue)}</strong></dd>
      </dl>

      {awaitingDeposit && payment.depositDueAt && (
        <Alert tone="info">
          Pague o sinal até <strong>{formatDateTime(payment.depositDueAt)}</strong>. Sem o pagamento nesse prazo, o pedido
          é cancelado automaticamente e o horário é liberado.
        </Alert>
      )}

      {payment.pixKey && (
        <div className={styles.fieldset}>
          <p style={{ margin: 0 }}>
            {awaitingDeposit
              ? `Para confirmar sua data, faça um Pix de ${formatBRL(payment.depositAmount)} para:`
              : `O restante (${formatBRL(payment.balanceDue)}) é pago na retirada, por Pix ou em dinheiro. Pix:`}
          </p>
          <output className={styles.code} style={{ fontSize: '1rem' }}>{payment.pixKey}</output>
          {payment.pixHolderName && <p className={styles.hint}>Titular: {payment.pixHolderName}</p>}
          <Button variant="secondary" onClick={() => void copyPix()}>{copied ? 'Chave copiada!' : 'Copiar chave Pix'}</Button>
        </div>
      )}
      {canPayByCard && (
        <div className={styles.fieldset}>
          <p style={{ margin: 0 }}>
            {payment.pixKey ? 'Ou pague o sinal no cartão de crédito pelo link de pagamento:' : 'Pague o sinal no cartão de crédito pelo link de pagamento:'}
          </p>
          <a className={styles.code} style={{ fontSize: '1rem' }} href={payment.cardPaymentLink!} target="_blank"
            rel="noopener noreferrer">
            Abrir link de pagamento
          </a>
          <p className={styles.hint}>O valor do sinal é {formatBRL(payment.depositAmount)}.</p>
        </div>
      )}
      {awaitingDeposit && !payment.pixKey && !payment.cardPaymentLink && (
        <p className={styles.hint}>Fale com a confeitaria pelo WhatsApp para combinar o pagamento do sinal.</p>
      )}

      {payment.canSubmitProof && (
        <div className={styles.fieldset}>
          <p style={{ margin: 0 }}>Depois de pagar, envie o comprovante (JPG, PNG ou PDF, até 5 MB):</p>
          {canPayByCard && (
            <fieldset className={styles.fieldset} style={{ border: 0, padding: 0, margin: 0 }}>
              <legend className={styles.legend}>Como você pagou?</legend>
              <div className={styles.choiceGrid}>
                {(['PIX', 'CARD_LINK'] as const).map((option) => (
                  <label key={option} className={styles.choice}>
                    <input type="radio" name="proofMethod" checked={method === option}
                      onChange={() => setMethod(option)} />
                    <span className={styles.choiceTitle}>{option === 'PIX' ? 'Pix' : 'Cartão (link de pagamento)'}</span>
                  </label>
                ))}
              </div>
            </fieldset>
          )}
          <p className={styles.hint}>
            O envio do comprovante não confirma o pedido sozinho: a confeitaria confere o pagamento e confirma.
          </p>
          <input ref={inputRef} type="file" accept={ACCEPTED} hidden onChange={(e) => void upload(e.target.files)} />
          <Button onClick={() => inputRef.current?.click()} loading={sending}>Enviar comprovante</Button>
        </div>
      )}
      {error && <Alert tone="error">{error}</Alert>}

      {payment.payments.length > 0 && (
        <ul className={styles.fieldset} style={{ listStyle: 'none', padding: 0, margin: 0 }}>
          {payment.payments.map((entry, index) => (
            <li key={index}>
              <strong>{KIND[entry.kind]} · {formatBRL(entry.amount)}</strong>
              {entry.method === 'CARD_LINK' ? ' (cartão)' : entry.method === 'PIX' ? ' (Pix)' : ''} — {STATUS[entry.status]}
              <div className={styles.hint}>
                {formatDateTime(entry.createdAt)}
                {entry.status === 'REJECTED' && entry.reviewNote && ` · Motivo: ${entry.reviewNote}`}
                {entry.proofFileId && (
                  <>
                    {' · '}
                    <a href={trackingFileUrl(entry.proofFileId)} target="_blank" rel="noreferrer">ver comprovante</a>
                  </>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
