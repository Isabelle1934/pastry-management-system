import { useState } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { SelectField } from '../../../components/SelectField'
import { TextAreaField } from '../../../components/TextAreaField'
import { TextField } from '../../../components/TextField'
import { deleteQuoteDraft, saveQuoteDraft, sendQuote } from '../../../services/orders'
import type { CommercialStatus, OrderDetail, Quote, QuoteBreakdown, QuoteItem, QuoteItemKind } from '../../../types/order'
import { depositOf, formatBRL, fromCents, lineTotal, parseMoneyInput, toCents, toMoneyInput } from '../../../utils/money'
import { BREAKDOWN_ROWS, breakdownOf, QUOTE_ITEM_KINDS, QUOTE_KIND_LABELS } from '../../../utils/quote'
import { formatDateTime, QUOTE_STATUS } from '../../../utils/orderStatus'
import panel from '../panel.module.css'
import { describeSaveError } from '../saveErrors'
import styles from './orders.module.css'
import { StatusChip } from './StatusChip'
import { DELIVERY_ZONE_LABELS } from '../../../utils/productRules'

/** Situações em que a proprietária pode montar/enviar orçamento (mesma regra do servidor). */
const QUOTABLE: CommercialStatus[] = ['PENDING_REVIEW', 'QUOTE_SENT', 'AWAITING_DEPOSIT', 'DECLINED_BY_CLIENT']

interface ItemForm {
  key: number
  kind: QuoteItemKind
  name: string
  description: string
  quantity: string
  unitPrice: string
}

let nextKey = 1
const emptyItem = (): ItemForm => ({ key: nextKey++, kind: 'EXTRA', name: '', description: '', quantity: '1', unitPrice: '' })

function toItemForm(item: QuoteItem): ItemForm {
  return {
    key: nextKey++,
    kind: item.kind,
    name: item.name,
    description: item.description ?? '',
    quantity: toMoneyInput(item.quantity).replace(/,00$/, ''),
    unitPrice: toMoneyInput(item.unitPrice),
  }
}

/**
 * Sugestão de itens a partir do pedido: produtos do catálogo com o preço da encomenda, a taxa de entrega
 * das Configurações e, sem valor definido, o bolo personalizado e o topo (cobrado à parte).
 */
function itemsFromOrder(order: OrderDetail): ItemForm[] {
  const deliveryFee = order.suggestedDeliveryFee
  const items: ItemForm[] = order.items.map((item) => ({
    key: nextKey++,
    kind: 'PRODUCT' as const,
    name: item.name,
    description: item.notes ?? '',
    quantity: String(item.quantity),
    unitPrice: item.unitPrice == null ? '' : toMoneyInput(item.unitPrice),
  }))
  if (order.customCake) {
    const flavors = [...order.flavors, ...order.fillings].map((o) => o.name).join(', ')
    const cake = order.sizeName ?? order.cakeCategoryName ?? ''
    // Preço inicial = tabela (cópia do pedido); "consulte valores" fica em branco para a proprietária avaliar.
    items.push({ key: nextKey++, kind: 'PRODUCT', name: `Bolo personalizado – ${cake}`.trim(),
      description: flavors, quantity: '1', unitPrice: order.sizePrice ? toMoneyInput(order.sizePrice) : '' })
    if (order.decoration) {
      items.push({ key: nextKey++, kind: 'EXTRA', name: 'Decoração personalizada', description: order.decoration.slice(0, 300),
        quantity: '1', unitPrice: '' })
    }
  }
  if (order.customCake && order.topperRequested) {
    items.push({ key: nextKey++, kind: 'TOPPER', name: 'Topo de bolo', description: order.topperTheme ?? '', quantity: '1',
      unitPrice: '' })
  }
  if (order.fulfillmentType === 'DELIVERY') {
    const zone = order.deliveryZone ? DELIVERY_ZONE_LABELS[order.deliveryZone] : 'Entrega'
    items.push({ key: nextKey++, kind: 'DELIVERY', name: `Taxa de entrega – ${zone}`.slice(0, 120),
      description: order.deliveryDistrict ?? '',
      quantity: '1', unitPrice: deliveryFee ? toMoneyInput(deliveryFee) : '' })
  }
  return items
}

/** Subtotais por grupo, total, sinal de 50% e saldo restante. */
function Totals({ breakdown, total, deposit, balance }: { breakdown: QuoteBreakdown; total: string; deposit: string;
  balance: string }) {
  return (
    <div className={styles.totals} aria-live="polite">
      {BREAKDOWN_ROWS.filter((row) => row.key === 'products' || breakdown[row.key] !== '0.00').map((row) => (
        <span key={row.key}>{row.label}: {formatBRL(breakdown[row.key])}</span>
      ))}
      <strong>Total: {formatBRL(total)}</strong>
      <span>Sinal (50%): {formatBRL(deposit)}</span>
      <span>Restante na retirada/entrega (50%): {formatBRL(balance)}</span>
    </div>
  )
}

function QuoteTable({ quote }: {
  quote: Pick<Quote, 'items' | 'breakdown' | 'total' | 'depositAmount' | 'balanceAmount'>
}) {
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
                  <div className={styles.muted}>
                    {QUOTE_KIND_LABELS[item.kind]}{item.description ? ` · ${item.description}` : ''}
                  </div>
                </td>
                <td className={styles.number}>{toMoneyInput(item.quantity).replace(/,00$/, '')}</td>
                <td className={styles.number}>{formatBRL(item.unitPrice)}</td>
                <td className={styles.number}>{formatBRL(item.subtotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Totals breakdown={quote.breakdown} total={quote.total} deposit={quote.depositAmount}
        balance={quote.balanceAmount} />
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

function QuoteEditor({ order, initialItems, initialNotes, initialValidity, hasDraft, onChanged, onClose }:
  EditorProps) {
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
  const lineCents = parsed.map((p) => (p.quantity && p.unitPrice ? toCents(lineTotal(p.quantity, p.unitPrice)) : 0))
  const totalCents = lineCents.reduce((sum, cents) => sum + cents, 0)
  const breakdown = breakdownOf(items.map((item, index) => ({ kind: item.kind, cents: lineCents[index] })))
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
          kind: item.kind,
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
            <SelectField label="Tipo" value={item.kind}
              onChange={(e) => change(item.key, { kind: e.target.value as QuoteItemKind })}
              options={QUOTE_ITEM_KINDS.map((kind) => ({ value: kind, label: QUOTE_KIND_LABELS[kind] }))} />
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
        <Totals breakdown={breakdown} total={total} deposit={deposit} balance={balance} />
        <p className={styles.muted}>
          Os valores são calculados automaticamente (preço da tabela, produtos e taxa da modalidade de entrega), mas
          a palavra final é sua: ajuste qualquer item antes de enviar. Decoração, adicionais e topo (fornecedor externo)
          entram separados. Entrega fora da cidade: informe a taxa combinada.
        </p>
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
