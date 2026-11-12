import { useState } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { TextAreaField } from '../../../components/TextAreaField'
import { TextField } from '../../../components/TextField'
import { deleteQuoteDraft, saveQuoteDraft, sendQuote } from '../../../services/orders'
import type { CommercialStatus, OrderDetail, Quote, QuoteItem } from '../../../types/order'
import { depositOf, formatBRL, fromCents, lineTotal, parseMoneyInput, toCents, toMoneyInput } from '../../../utils/money'
import { formatDateTime, QUOTE_STATUS } from '../../../utils/orderStatus'
import panel from '../panel.module.css'
import { describeSaveError } from '../saveErrors'
import styles from './orders.module.css'
import { StatusChip } from './StatusChip'

/** Situações em que a proprietária pode montar/enviar orçamento (mesma regra do servidor). */
const QUOTABLE: CommercialStatus[] = ['PENDING_REVIEW', 'QUOTE_SENT', 'AWAITING_DEPOSIT', 'DECLINED_BY_CLIENT']

interface ItemForm {
  key: number
  name: string
  description: string
  quantity: string
  unitPrice: string
}

let nextKey = 1
const emptyItem = (): ItemForm => ({ key: nextKey++, name: '', description: '', quantity: '1', unitPrice: '' })

function toItemForm(item: QuoteItem): ItemForm {
  return {
    key: nextKey++,
    name: item.name,
    description: item.description ?? '',
    quantity: toMoneyInput(item.quantity).replace(/,00$/, ''),
    unitPrice: toMoneyInput(item.unitPrice),
  }
}

/**
 * Sugestão de itens a partir do pedido: produtos do catálogo com o preço da encomenda e, sem valor
 * definido, o bolo personalizado, o topo (cobrado à parte) e a taxa de entrega.
 */
function itemsFromOrder(order: OrderDetail): ItemForm[] {
  const items: ItemForm[] = order.items.map((item) => ({
    key: nextKey++,
    name: item.name,
    description: item.notes ?? '',
    quantity: String(item.quantity),
    unitPrice: item.unitPrice == null ? '' : toMoneyInput(item.unitPrice),
  }))
  if (order.customCake) {
    const flavors = [...order.flavors, ...order.fillings].map((o) => o.name).join(', ')
    items.push({ key: nextKey++, name: `Bolo personalizado – ${order.sizeName ?? ''}`.trim(), description: flavors,
      quantity: '1', unitPrice: '' })
  }
  if (order.customCake && order.topperRequested) {
    items.push({ key: nextKey++, name: 'Topo de bolo', description: order.topperTheme ?? '', quantity: '1', unitPrice: '' })
  }
  if (order.fulfillmentType === 'DELIVERY') {
    items.push({ key: nextKey++, name: 'Taxa de entrega', description: order.deliveryDistrict ?? '', quantity: '1',
      unitPrice: '' })
  }
  return items
}

function QuoteTable({ quote }: { quote: Pick<Quote, 'items' | 'total' | 'depositAmount' | 'balanceAmount'> }) {
  return (
    <>
      <div style={{ overflowX: 'auto' }}>
        <table className={styles.quoteTable}>
          <thead>
            <tr>
              <th>Item</th>
              <th className={styles.number}>Qtd.</th>
              <th className={styles.number}>Valor</th>
              <th className={styles.number}>Subtotal</th>
            </tr>
          </thead>
          <tbody>
            {quote.items.map((item, index) => (
              <tr key={index}>
                <td>
                  {item.name}
                  {item.description && <div className={styles.muted}>{item.description}</div>}
                </td>
                <td className={styles.number}>{toMoneyInput(item.quantity).replace(/,00$/, '')}</td>
                <td className={styles.number}>{formatBRL(item.unitPrice)}</td>
                <td className={styles.number}>{formatBRL(item.subtotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className={styles.totals}>
        <strong>Total: {formatBRL(quote.total)}</strong>
        <span>Sinal (50%): {formatBRL(quote.depositAmount)}</span>
        <span>Restante na retirada/entrega: {formatBRL(quote.balanceAmount)}</span>
      </div>
    </>
  )
}

interface EditorProps {
  order: OrderDetail
  initialItems: ItemForm[]
  initialNotes: string
  initialValidity: string
  hasDraft: boolean
  onChanged: () => Promise<void>
  onClose: () => void
}

function QuoteEditor({ order, initialItems, initialNotes, initialValidity, hasDraft, onChanged, onClose }: EditorProps) {
  const [items, setItems] = useState<ItemForm[]>(initialItems.length ? initialItems : [emptyItem()])
  const [notes, setNotes] = useState(initialNotes)
  const [validity, setValidity] = useState(initialValidity)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string }>()
  const [busy, setBusy] = useState(false)
  const [confirmSend, setConfirmSend] = useState(false)

  const parsed = items.map((item) => ({
    quantity: /^\d{1,4}(,\d{1,2})?$/.test(item.quantity.trim()) ? item.quantity.trim().replace(',', '.') : null,
    unitPrice: parseMoneyInput(item.unitPrice),
  }))
  const totalCents = parsed.reduce(
    (sum, p) => (p.quantity && p.unitPrice ? sum + toCents(lineTotal(p.quantity, p.unitPrice)) : sum),
    0,
  )
  const total = fromCents(totalCents)
  const { deposit, balance } = depositOf(total)

  const change = (key: number, patch: Partial<ItemForm>) =>
    setItems((current) => current.map((item) => (item.key === key ? { ...item, ...patch } : item)))

  function validate(): boolean {
    const next: Record<string, string> = {}
    items.forEach((item, index) => {
      if (!item.name.trim()) next[`name-${index}`] = 'Informe o item.'
      if (!parsed[index].quantity) next[`quantity-${index}`] = 'Quantidade inválida.'
      if (!parsed[index].unitPrice) next[`unitPrice-${index}`] = 'Valor inválido.'
    })
    if (validity.trim() && !/^\d+$/.test(validity.trim())) next.validity = 'Use um número de horas.'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function save(): Promise<boolean> {
    if (!validate()) return false
    setBusy(true)
    setNotice(undefined)
    try {
      await saveQuoteDraft(order.id, {
        items: items.map((item, index) => ({
          name: item.name.trim(),
          description: item.description.trim(),
          quantity: parsed[index].quantity!,
          unitPrice: parsed[index].unitPrice!,
        })),
        notes: notes.trim(),
        validityHours: validity.trim() ? Number(validity) : null,
      })
      return true
    } catch (error) {
      setNotice({ tone: 'error', text: describeSaveError(error).message ?? 'Verifique os itens do orçamento.' })
      return false
    } finally {
      setBusy(false)
    }
  }

  async function saveDraft() {
    if (await save()) {
      await onChanged()
      setNotice({ tone: 'success', text: 'Rascunho salvo. A cliente ainda não vê este orçamento.' })
    }
  }

  async function saveAndSend() {
    if (!(await save())) return
    setBusy(true)
    try {
      await sendQuote(order.id)
      await onChanged()
      onClose()
    } catch (error) {
      setNotice({ tone: 'error', text: describeSaveError(error).message ?? 'Não foi possível enviar.' })
    } finally {
      setBusy(false)
      setConfirmSend(false)
    }
  }

  async function discard() {
    setBusy(true)
    try {
      if (hasDraft) await deleteQuoteDraft(order.id)
      await onChanged()
      onClose()
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={panel.formCard}>
      <h3>Montar orçamento</h3>
      <div className={panel.form}>
        {items.map((item, index) => (
          <div key={item.key} className={styles.itemRow}>
            <TextField label="Item" value={item.name} onChange={(e) => change(item.key, { name: e.target.value })}
              error={errors[`name-${index}`]} maxLength={120} />
            <TextField label="Descrição (opcional)" value={item.description}
              onChange={(e) => change(item.key, { description: e.target.value })} maxLength={300} />
            <TextField label="Qtd." inputMode="decimal" value={item.quantity}
              onChange={(e) => change(item.key, { quantity: e.target.value })} error={errors[`quantity-${index}`]} />
            <TextField label="Valor (R$)" inputMode="decimal" placeholder="0,00" value={item.unitPrice}
              onChange={(e) => change(item.key, { unitPrice: e.target.value })} error={errors[`unitPrice-${index}`]} />
            <Button variant="secondary" disabled={items.length === 1}
              onClick={() => setItems((current) => current.filter((i) => i.key !== item.key))}
              aria-label={`Remover item ${index + 1}`}>
              Remover
            </Button>
          </div>
        ))}
        <Button variant="secondary" onClick={() => setItems((current) => [...current, emptyItem()])}>
          Adicionar item
        </Button>
        <Button variant="secondary" onClick={() => setItems((current) => [
          ...current.filter((i) => i.name.trim() || i.unitPrice.trim()), ...itemsFromOrder(order)])}>
          Incluir itens do pedido
        </Button>
        <div className={styles.totals} aria-live="polite">
          <strong>Total: {formatBRL(total)}</strong>
          <span>Sinal (50%): {formatBRL(deposit)}</span>
          <span>Restante na retirada/entrega: {formatBRL(balance)}</span>
        </div>
        <TextAreaField label="Observações para a cliente (opcional)" value={notes}
          onChange={(e) => setNotes(e.target.value)} maxLength={1000} rows={2} />
        <TextField label="Validade (horas)" inputMode="numeric" value={validity}
          onChange={(e) => setValidity(e.target.value)} error={errors.validity}
          hint="Deixe em branco para usar o padrão das Configurações." />
        {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
        {confirmSend ? (
          <Alert tone="info">
            <p style={{ margin: '0 0 var(--space-2)' }}>
              Enviar este orçamento de {formatBRL(total)} para a cliente?
              {order.quotes.some((q) => q.status === 'SENT' || q.status === 'ACCEPTED') &&
                ' A versão atual será substituída e a cliente precisará aceitar de novo.'}
            </p>
            <div className={panel.actions}>
              <Button loading={busy} onClick={() => void saveAndSend()}>Confirmar envio</Button>
              <Button variant="secondary" onClick={() => setConfirmSend(false)} disabled={busy}>Voltar</Button>
            </div>
          </Alert>
        ) : (
          <div className={panel.actions}>
            <Button onClick={() => validate() && setConfirmSend(true)} disabled={busy || totalCents === 0}>
              Enviar para a cliente
            </Button>
            <Button variant="secondary" onClick={() => void saveDraft()} loading={busy}>Salvar rascunho</Button>
            <Button variant="secondary" onClick={() => void discard()} disabled={busy}>
              {hasDraft ? 'Descartar rascunho' : 'Cancelar'}
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}

export function QuotePanel({ order, onChanged }: { order: OrderDetail; onChanged: () => Promise<void> }) {
  const [editing, setEditing] = useState(false)
  const draft = order.quotes.find((q) => q.status === 'DRAFT')
  const current = order.quotes.find((q) => q.status === 'SENT' || q.status === 'ACCEPTED')
  const previous = order.quotes.filter((q) => q !== draft && q !== current)
  const canQuote = QUOTABLE.includes(order.commercialStatus)
  const base = draft ?? current ?? order.quotes.find((q) => q.status !== 'DRAFT')

  return (
    <section className={`${styles.section} ${styles.wide}`} aria-labelledby="quote-title">
      <div className={styles.row}>
        <h2 id="quote-title">Orçamento</h2>
        {canQuote && !editing && (
          <Button onClick={() => setEditing(true)}>
            {draft ? 'Continuar rascunho' : current ? 'Nova versão' : 'Montar orçamento'}
          </Button>
        )}
      </div>

      {editing && (
        <QuoteEditor
          order={order}
          initialItems={base ? base.items.map(toItemForm) : itemsFromOrder(order)}
          initialNotes={base?.notes ?? ''}
          initialValidity={draft ? String(draft.validityHours) : ''}
          hasDraft={!!draft}
          onChanged={onChanged}
          onClose={() => setEditing(false)}
        />
      )}

      {!editing && draft && <Alert tone="info">Há um rascunho de orçamento ainda não enviado.</Alert>}

      {current ? (
        <div className={panel.form}>
          <div className={styles.row}>
            <span>Versão {current.versionNumber}</span>
            <StatusChip {...QUOTE_STATUS[current.status]} />
          </div>
          {current.status === 'SENT' && current.validUntil && (
            <p className={styles.muted}>Válido até {formatDateTime(current.validUntil)}</p>
          )}
          <QuoteTable quote={current} />
          {current.notes && <p className={styles.muted}>Observações: {current.notes}</p>}
        </div>
      ) : (
        !draft && <p className={styles.muted}>Nenhum orçamento enviado.</p>
      )}

      {previous.length > 0 && (
        <ul className={styles.versions} aria-label="Versões anteriores">
          {previous.map((quote) => (
            <li key={quote.id}>
              <details>
                <summary>
                  Versão {quote.versionNumber} · {formatBRL(quote.total)} · {QUOTE_STATUS[quote.status].label}
                </summary>
                <QuoteTable quote={quote} />
                {quote.declineReason && <p className={styles.muted}>Motivo da recusa: {quote.declineReason}</p>}
              </details>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
