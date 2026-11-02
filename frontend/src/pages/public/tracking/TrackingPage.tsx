import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { TextField } from '../../../components/TextField'
import { useBakeryInfo } from '../../../hooks/useBakeryInfo'
import { PublicLayout } from '../../../layouts/PublicLayout'
import { ApiError } from '../../../services/api'
import { acceptQuote, currentTracking, declineQuote, leaveTracking, verifyTracking } from '../../../services/tracking'
import type { CommercialStatus, TrackingView } from '../../../types/order'
import { formatLongDay } from '../../../utils/date'
import { formatBRL, toMoneyInput } from '../../../utils/money'
import { formatDateTime } from '../../../utils/orderStatus'
import styles from '../order/order.module.css'

function statusMessage(view: TrackingView): { title: string; text: string; tone: 'info' | 'success' | 'error' } {
  const deposit = view.quote ? formatBRL(view.quote.depositAmount) : ''
  const messages: Record<CommercialStatus, { title: string; text: string; tone: 'info' | 'success' | 'error' }> = {
    PENDING_REVIEW: {
      title: 'Em análise',
      text: 'Recebemos sua solicitação e estamos preparando o orçamento. Ele aparecerá aqui.',
      tone: 'info',
    },
    QUOTE_SENT: { title: 'Orçamento disponível', text: 'Confira o orçamento abaixo e responda dentro do prazo.', tone: 'info' },
    AWAITING_DEPOSIT: {
      title: 'Orçamento aceito',
      text: `Para garantir a data, é necessário pagar o sinal de ${deposit}. Fale com a confeitaria pelo WhatsApp para combinar o pagamento.`,
      tone: 'info',
    },
    CONFIRMED: { title: 'Pedido confirmado', text: 'Sua data está garantida. Até a retirada!', tone: 'success' },
    COMPLETED: { title: 'Pedido entregue', text: 'Obrigada pela preferência!', tone: 'success' },
    REJECTED: {
      title: 'Não foi possível atender',
      text: 'Infelizmente não conseguiremos atender esta encomenda. Fale com a confeitaria se tiver dúvidas.',
      tone: 'error',
    },
    DECLINED_BY_CLIENT: {
      title: 'Orçamento recusado',
      text: 'Você recusou o orçamento. Se mudar de ideia, fale com a confeitaria.',
      tone: 'error',
    },
    CANCELLED: { title: 'Pedido cancelado', text: 'Este pedido foi cancelado.', tone: 'error' },
  }
  return messages[view.commercialStatus]
}

function Lookup({ onFound }: { onFound: (view: TrackingView) => void }) {
  const [code, setCode] = useState('')
  const [digits, setDigits] = useState('')
  const [errors, setErrors] = useState<{ code?: string; digits?: string }>({})
  const [formError, setFormError] = useState<string>()
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const next = {
      code: code.replace(/[\s-]/g, '').length === 16 ? undefined : 'O código tem 16 caracteres (ex.: ABCD-1234-EFGH-5678).',
      digits: /^\d{4}$/.test(digits) ? undefined : 'Informe os 4 últimos dígitos do WhatsApp.',
    }
    setErrors(next)
    setFormError(undefined)
    if (next.code || next.digits) return
    setLoading(true)
    try {
      onFound(await verifyTracking(code, digits))
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Não foi possível consultar. Tente novamente.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className={styles.card} aria-labelledby="lookup-title">
      <h2 id="lookup-title">Consultar pedido</h2>
      <p className={styles.hint}>Use o código recebido ao fazer a encomenda.</p>
      <form className={styles.fieldset} onSubmit={handleSubmit} noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}
        <TextField label="Código de acompanhamento" value={code} onChange={(e) => setCode(e.target.value)}
          error={errors.code} autoCapitalize="characters" autoComplete="off" maxLength={24} placeholder="XXXX-XXXX-XXXX-XXXX" />
        <TextField label="4 últimos dígitos do seu WhatsApp" inputMode="numeric" value={digits}
          onChange={(e) => setDigits(e.target.value.replace(/\D/g, '').slice(0, 4))} error={errors.digits} />
        <Button type="submit" loading={loading}>Consultar</Button>
      </form>
    </section>
  )
}

function QuoteCard({ view, onChange }: { view: TrackingView; onChange: (view: TrackingView) => void }) {
  const quote = view.quote!
  const [declining, setDeclining] = useState(false)
  const [confirmingAccept, setConfirmingAccept] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const awaiting = quote.status === 'SENT' && view.commercialStatus === 'QUOTE_SENT'

  async function run(action: () => Promise<TrackingView>) {
    setBusy(true)
    setError(undefined)
    try {
      onChange(await action())
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Não foi possível concluir. Tente novamente.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className={styles.card} aria-labelledby="quote-title">
      <h2 id="quote-title">Orçamento</h2>
      <dl className={styles.reviewList}>
        {quote.items.map((item, index) => (
          <div key={index} style={{ display: 'contents' }}>
            <dt>
              {item.name}
              {item.quantity !== '1.00' && ` (${toMoneyInput(item.quantity).replace(/,00$/, '')}×)`}
              {item.description && <div style={{ fontSize: '0.85rem' }}>{item.description}</div>}
            </dt>
            <dd style={{ textAlign: 'right' }}>{formatBRL(item.subtotal)}</dd>
          </div>
        ))}
        <dt><strong>Total</strong></dt>
        <dd style={{ textAlign: 'right' }}><strong>{formatBRL(quote.total)}</strong></dd>
        <dt>Sinal (50%) para confirmar</dt>
        <dd style={{ textAlign: 'right' }}>{formatBRL(quote.depositAmount)}</dd>
        <dt>Restante na retirada</dt>
        <dd style={{ textAlign: 'right' }}>{formatBRL(quote.balanceAmount)}</dd>
      </dl>
      {quote.notes && <p className={styles.hint}>{quote.notes}</p>}
      {awaiting && quote.validUntil && <p><strong>Responda até {formatDateTime(quote.validUntil)}.</strong></p>}
      {error && <Alert tone="error">{error}</Alert>}

      {awaiting && !declining && !confirmingAccept && (
        <div className={styles.nav}>
          <Button variant="secondary" onClick={() => setDeclining(true)}>Recusar</Button>
          <Button onClick={() => setConfirmingAccept(true)}>Aceitar orçamento</Button>
        </div>
      )}
      {awaiting && confirmingAccept && (
        <Alert tone="info">
          <p style={{ margin: '0 0 var(--space-2)' }}>
            Ao aceitar, sua data só fica garantida após o pagamento do sinal de {formatBRL(quote.depositAmount)}.
            Depois do aceite, alterações no pedido são feitas pela confeitaria.
          </p>
          <div className={styles.nav}>
            <Button variant="secondary" onClick={() => setConfirmingAccept(false)} disabled={busy}>Voltar</Button>
            <Button loading={busy} onClick={() => void run(() => acceptQuote(quote.id))}>Confirmar aceite</Button>
          </div>
        </Alert>
      )}
      {awaiting && declining && (
        <div className={styles.fieldset}>
          <TextField label="Quer contar o motivo? (opcional)" value={reason} onChange={(e) => setReason(e.target.value)}
            maxLength={300} />
          <div className={styles.nav}>
            <Button variant="secondary" onClick={() => setDeclining(false)} disabled={busy}>Voltar</Button>
            <Button loading={busy} onClick={() => void run(() => declineQuote(quote.id, reason.trim()))}>
              Confirmar recusa
            </Button>
          </div>
        </div>
      )}
    </section>
  )
}

function OrderView({ view, onChange, onLeave }: { view: TrackingView; onChange: (v: TrackingView) => void; onLeave: () => void }) {
  const { data: bakery } = useBakeryInfo()
  const location = useLocation()
  const status = statusMessage(view)
  const savedNotice = (location.state as { saved?: boolean } | null)?.saved

  return (
    <>
      {savedNotice && (
        <Alert tone="success">
          Alterações salvas. {view.commercialStatus === 'PENDING_REVIEW' && 'A confeitaria vai analisar seu pedido novamente.'}
        </Alert>
      )}
      <Alert tone={status.tone}>
        <strong>{status.title}.</strong> {status.text}
      </Alert>
      {view.quote && <QuoteCard view={view} onChange={onChange} />}
      <section className={styles.card} aria-labelledby="order-title">
        <h2 id="order-title">Seu pedido</h2>
        <dl className={styles.reviewList}>
          <dt>Código</dt><dd>{view.trackingCode}</dd>
          <dt>Nome</dt><dd>{view.customerName}</dd>
          <dt>WhatsApp</dt><dd>{view.whatsappMasked}</dd>
          <dt>Retirada</dt><dd>{formatLongDay(view.pickupDate)} às {view.pickupTime}</dd>
          <dt>Tamanho</dt><dd>{view.sizeName}</dd>
          <dt>Massa</dt><dd>{view.flavors.map((f) => f.name).join(', ')}</dd>
          <dt>Recheio</dt><dd>{view.fillings.map((f) => f.name).join(', ')}</dd>
          {view.decoration && (<><dt>Decoração</dt><dd>{view.decoration}</dd></>)}
          {view.cakeText && (<><dt>Nome/frase</dt><dd>{view.cakeText}</dd></>)}
          {view.notes && (<><dt>Observações</dt><dd>{view.notes}</dd></>)}
          <dt>Topo</dt><dd>{view.topperRequested ? [view.topperTheme, view.topperName, view.topperAge].filter(Boolean).join(' · ') : 'Não'}</dd>
          <dt>Fotos</dt><dd>{view.photoCount}</dd>
        </dl>
        {view.editable && (
          <p>
            <Link to="/acompanhar/editar">Alterar pedido</Link>
            {view.commercialStatus === 'QUOTE_SENT' && (
              <span className={styles.hint}> — o orçamento atual deixará de valer e será refeito.</span>
            )}
          </p>
        )}
        {bakery?.whatsappLink && (
          <p className={styles.hint}>
            Dúvidas? <a href={bakery.whatsappLink} target="_blank" rel="noopener noreferrer">Fale pelo WhatsApp</a>.
          </p>
        )}
      </section>
      <div className={styles.nav}>
        <span />
        <Button variant="secondary" onClick={onLeave}>Sair</Button>
      </div>
    </>
  )
}

export function TrackingPage() {
  const [view, setView] = useState<TrackingView | null>()
  const [error, setError] = useState<string>()

  useEffect(() => {
    let active = true
    currentTracking().then(
      (current) => active && setView(current),
      () => active && setError('Não foi possível carregar. Tente novamente.'),
    )
    return () => {
      active = false
    }
  }, [])

  async function leave() {
    try {
      await leaveTracking()
    } finally {
      setView(null)
    }
  }

  return (
    <PublicLayout>
      <div className={styles.page}>
        <h1>Acompanhar pedido</h1>
        {error && <Alert tone="error">{error}</Alert>}
        {view === undefined && !error && <p role="status">Carregando…</p>}
        {view === null && <Lookup onFound={setView} />}
        {view && <OrderView view={view} onChange={setView} onLeave={() => void leave()} />}
      </div>
    </PublicLayout>
  )
}
