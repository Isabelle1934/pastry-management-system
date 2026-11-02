import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { TextField } from '../../../components/TextField'
import { useLoader } from '../../../hooks/useLoader'
import { cancelOrder, getOrder, orderFileUrl, rejectOrder } from '../../../services/orders'
import type { OrderDetail } from '../../../types/order'
import { formatLongDay } from '../../../utils/date'
import {
  COMMERCIAL_STATUS,
  formatDateTime,
  PAYMENT_STATUS,
  PRODUCTION_STATUS,
} from '../../../utils/orderStatus'
import panel from '../panel.module.css'
import { describeSaveError } from '../saveErrors'
import { OrderEditForm } from './OrderEditForm'
import { PaymentsPanel } from './PaymentsPanel'
import { ProductionPanel } from './ProductionPanel'
import styles from './orders.module.css'
import { QuotePanel } from './QuotePanel'
import { StatusChip } from './StatusChip'

const ACTOR = { OWNER: 'Você', CLIENT: 'Cliente', SYSTEM: 'Sistema' } as const
const CLOSED = ['COMPLETED', 'REJECTED', 'CANCELLED']

type PendingAction = { kind: 'reject' | 'cancel'; reason: string; error?: string }

function Item({ label, value }: { label: string; value: string | number | null | undefined }) {
  if (value == null || value === '') return null
  return (
    <>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </>
  )
}

function Photos({ order }: { order: OrderDetail }) {
  const photos = order.files.filter((f) => f.purpose !== 'PAYMENT_PROOF')
  if (photos.length === 0) return <p className={styles.muted}>Nenhuma foto enviada.</p>
  return (
    <div className={styles.photos}>
      {photos.map((file) => (
        <a key={file.id} className={styles.photo} href={orderFileUrl(order.id, file.id)} target="_blank" rel="noreferrer"
          title={file.originalName}>
          <img src={orderFileUrl(order.id, file.id)}
            alt={`${file.purpose === 'TOPPER_REFERENCE' ? 'Referência do topo' : 'Referência do bolo'}: ${file.originalName}`} />
        </a>
      ))}
    </div>
  )
}

export function OrderDetailPage() {
  const id = Number(useParams().id)
  const load = useCallback(() => getOrder(id), [id])
  const { data: order, loading, error, reload, setData } = useLoader(load)
  const [editing, setEditing] = useState(false)
  const [pending, setPending] = useState<PendingAction>()
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string }>()
  const [busy, setBusy] = useState(false)

  const refresh = useCallback(async () => {
    await reload()
  }, [reload])

  if (loading && !order) return <p role="status">Carregando…</p>
  if (error) return <Alert tone="error">{error}</Alert>
  if (!order) return null

  const canReject = order.commercialStatus === 'PENDING_REVIEW' || order.commercialStatus === 'QUOTE_SENT'
  const canCancel = !CLOSED.includes(order.commercialStatus)

  async function confirmAction() {
    if (!pending || !order) return
    if (!pending.reason.trim()) {
      setPending({ ...pending, error: 'Informe o motivo.' })
      return
    }
    setBusy(true)
    try {
      const updated = pending.kind === 'reject'
        ? await rejectOrder(order.id, pending.reason.trim(), order.version)
        : await cancelOrder(order.id, pending.reason.trim(), order.version)
      setData(() => updated)
      setNotice({ tone: 'success', text: pending.kind === 'reject' ? 'Solicitação recusada.' : 'Pedido cancelado.' })
      setPending(undefined)
    } catch (err) {
      const failure = describeSaveError(err)
      setPending({ ...pending, error: failure.message ?? Object.values(failure.fields)[0] })
      if (failure.stale) await refresh()
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={panel.page}>
      <p><Link to="/admin/pedidos">← Voltar para pedidos</Link></p>
      <header className={styles.header}>
        <h1>{order.customerName}</h1>
        <span className={styles.code}>Código {order.trackingCode}</span>
        <div className={styles.chips}>
          <StatusChip {...COMMERCIAL_STATUS[order.commercialStatus]} />
          <StatusChip label={`Pagamento: ${PAYMENT_STATUS[order.paymentStatus]}`} tone="neutral" />
          <StatusChip label={`Produção: ${PRODUCTION_STATUS[order.productionStatus]}`} tone="neutral" />
          {order.slotReserved && <StatusChip label="Vaga reservada" tone="ok" />}
        </div>
        {order.commercialStatus === 'CANCELLED' && (
          <Alert tone="error">
            Cancelado em {formatDateTime(order.cancelledAt)} ({order.cancelledBy ? ACTOR[order.cancelledBy] : ''}):{' '}
            {order.cancellationReason}
          </Alert>
        )}
        <div className={panel.actions}>
          <a className={styles.filter} style={{ display: 'inline-flex', alignItems: 'center', textDecoration: 'none' }}
            href={order.whatsappLink} target="_blank" rel="noopener noreferrer">
            WhatsApp {order.customerWhatsapp}
          </a>
          {!CLOSED.includes(order.commercialStatus) && !editing && (
            <Button variant="secondary" onClick={() => setEditing(true)}>Editar pedido</Button>
          )}
          {canReject && !pending && (
            <Button variant="secondary" onClick={() => setPending({ kind: 'reject', reason: '' })}>Recusar</Button>
          )}
          {order.commercialStatus !== 'REJECTED' && (
            <Link className={styles.filter} style={{ display: 'inline-flex', alignItems: 'center', textDecoration: 'none' }}
              to={`/admin/financeiro?aba=despesas&pedido=${encodeURIComponent(order.trackingCode)}`}>
              Registrar despesa deste pedido
            </Link>
          )}
          {canCancel && !pending && (
            <Button variant="secondary" onClick={() => setPending({ kind: 'cancel', reason: '' })}>Cancelar pedido</Button>
          )}
        </div>
      </header>

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}

      {pending && (
        <div className={panel.formCard}>
          <h3>{pending.kind === 'reject' ? 'Recusar solicitação' : 'Cancelar pedido'}</h3>
          <div className={panel.form}>
            {pending.kind === 'cancel' && order.slotReserved && (
              <Alert tone="info">A vaga reservada na agenda será liberada. Pagamentos registrados não são apagados.</Alert>
            )}
            <TextField label="Motivo" value={pending.reason} maxLength={300} error={pending.error} autoFocus
              onChange={(e) => setPending({ ...pending, reason: e.target.value, error: undefined })} />
            <div className={panel.actions}>
              <Button loading={busy} onClick={() => void confirmAction()}>
                {pending.kind === 'reject' ? 'Confirmar recusa' : 'Confirmar cancelamento'}
              </Button>
              <Button variant="secondary" onClick={() => setPending(undefined)} disabled={busy}>Voltar</Button>
            </div>
          </div>
        </div>
      )}

      {editing && (
        <OrderEditForm
          order={order}
          onSaved={(saved) => {
            setData(() => saved)
            setEditing(false)
            setNotice({ tone: 'success', text: 'Pedido atualizado.' })
          }}
          onCancel={() => setEditing(false)}
          onStale={() => void refresh()}
        />
      )}

      <div className={styles.grid}>
        <section className={styles.section}>
          <h2>Retirada</h2>
          <dl className={styles.dl}>
            <Item label="Data" value={formatLongDay(order.pickupDate)} />
            <Item label="Horário" value={order.pickupTime} />
            <Item label="Pedido em" value={formatDateTime(order.createdAt)} />
          </dl>
        </section>
        <section className={styles.section}>
          <h2>Bolo</h2>
          <dl className={styles.dl}>
            <Item label="Tamanho" value={order.sizeName} />
            <Item label="Massa" value={order.flavors.map((f) => f.name).join(', ')} />
            <Item label="Recheio" value={order.fillings.map((f) => f.name).join(', ')} />
            <Item label="Decoração" value={order.decoration} />
            <Item label="Nome/frase" value={order.cakeText} />
            <Item label="Observações" value={order.notes} />
          </dl>
        </section>
        <section className={styles.section}>
          <h2>Topo de bolo</h2>
          {order.topperRequested ? (
            <dl className={styles.dl}>
              <Item label="Tema" value={order.topperTheme} />
              <Item label="Nome" value={order.topperName} />
              <Item label="Idade" value={order.topperAge} />
            </dl>
          ) : (
            <p className={styles.muted}>Não solicitado.</p>
          )}
        </section>
        <section className={styles.section}>
          <h2>Fotos de referência</h2>
          <Photos order={order} />
        </section>

        <QuotePanel order={order} onChanged={refresh} />
        <PaymentsPanel order={order} onChanged={refresh} />
        <ProductionPanel order={order} onChanged={refresh} />

        <section className={`${styles.section} ${styles.wide}`}>
          <h2>Histórico</h2>
          <ol className={styles.history}>
            {[...order.history].reverse().map((entry, index) => (
              <li key={index}>
                <strong>{entry.description}</strong>
                <p className={styles.muted}>
                  {formatDateTime(entry.createdAt)} · {ACTOR[entry.actor]}
                  {typeof entry.details?.reason === 'string' && ` · Motivo: ${entry.details.reason}`}
                </p>
              </li>
            ))}
          </ol>
        </section>
      </div>
    </div>
  )
}
