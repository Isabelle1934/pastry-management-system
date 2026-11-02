import { useCallback, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { SelectField } from '../../../components/SelectField'
import { TextField } from '../../../components/TextField'
import { useLoader } from '../../../hooks/useLoader'
import { deleteExpense, listExpenseCategories, listExpenses, saveExpense } from '../../../services/finance'
import type { Expense, ExpenseCategory } from '../../../types/finance'
import { formatDate } from '../../../utils/date'
import { formatBRL, parseMoneyInput, toMoneyInput } from '../../../utils/money'
import panel from '../panel.module.css'
import { describeSaveError } from '../saveErrors'

interface FormProps {
  expense?: Expense
  categories: ExpenseCategory[]
  defaultDate: string
  presetOrderCode?: string
  onSaved: (expense: Expense) => void
  onCancel: () => void
  onStale: () => void
}

function ExpenseForm({ expense, categories, defaultDate, presetOrderCode, onSaved, onCancel, onStale }: FormProps) {
  const available = categories.filter((c) => c.active || c.id === expense?.categoryId)
  const [description, setDescription] = useState(expense?.description ?? '')
  const [amount, setAmount] = useState(toMoneyInput(expense?.amount))
  const [date, setDate] = useState(expense?.expenseDate ?? defaultDate)
  const [categoryId, setCategoryId] = useState(String(expense?.categoryId ?? available[0]?.id ?? ''))
  const [orderCode, setOrderCode] = useState(expense?.order?.trackingCode ?? presetOrderCode ?? '')
  const [notes, setNotes] = useState(expense?.notes ?? '')
  const [errors, setErrors] = useState<Record<string, string | undefined>>({})
  const [formError, setFormError] = useState<string>()
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const value = parseMoneyInput(amount)
    const nextErrors = {
      description: description.trim() ? undefined : 'Descreva a despesa.',
      amount: value && value !== '0.00' ? undefined : 'Informe o valor (ex.: 45,90).',
      expenseDate: /^\d{4}-\d{2}-\d{2}$/.test(date) ? undefined : 'Informe a data.',
      categoryId: categoryId ? undefined : 'Escolha a categoria.',
    }
    setErrors(nextErrors)
    setFormError(undefined)
    if (Object.values(nextErrors).some(Boolean)) return

    setSaving(true)
    try {
      onSaved(await saveExpense({
        description: description.trim(),
        amount: value!,
        expenseDate: date,
        categoryId: Number(categoryId),
        orderCode: orderCode.trim() || undefined,
        notes: notes.trim() || undefined,
        version: expense?.version,
      }, expense?.id))
    } catch (err) {
      const failure = describeSaveError(err)
      setErrors(failure.fields)
      setFormError(failure.message)
      if (failure.stale) onStale()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className={panel.formCard}>
      <h3>{expense ? 'Editar despesa' : 'Nova despesa'}</h3>
      <form className={panel.form} onSubmit={handleSubmit} noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}
        {available.length === 0 && (
          <Alert tone="info">Cadastre ou ative uma categoria na aba “Categorias” antes de registrar despesas.</Alert>
        )}
        <TextField label="Descrição" value={description} maxLength={200} autoFocus error={errors.description}
          onChange={(e) => setDescription(e.target.value)} placeholder="Ex.: Chocolate e leite condensado" />
        <div className={panel.row}>
          <TextField label="Valor (R$)" inputMode="decimal" value={amount} placeholder="0,00" error={errors.amount}
            onChange={(e) => setAmount(e.target.value)} />
          <TextField label="Data" type="date" value={date} error={errors.expenseDate}
            onChange={(e) => setDate(e.target.value)} />
        </div>
        <div className={panel.row}>
          <SelectField label="Categoria" value={categoryId} error={errors.categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
            options={available.map((c) => ({ value: String(c.id), label: c.active ? c.name : `${c.name} (inativa)` }))} />
          <TextField label="Código do pedido (opcional)" value={orderCode} maxLength={30} error={errors.orderCode}
            onChange={(e) => setOrderCode(e.target.value)} hint="Preencha se a despesa for de uma encomenda específica."
            placeholder="XXXX-XXXX-XXXX-XXXX" />
        </div>
        <TextField label="Observação (opcional)" value={notes} maxLength={300} error={errors.notes}
          onChange={(e) => setNotes(e.target.value)} />
        <div className={panel.actions}>
          <Button type="submit" loading={saving} disabled={available.length === 0}>Salvar</Button>
          <Button variant="secondary" onClick={onCancel} disabled={saving}>Cancelar</Button>
        </div>
      </form>
    </div>
  )
}

interface Props {
  from: string
  to: string
  presetOrderCode?: string
}

export function ExpensesSection({ from, to, presetOrderCode }: Props) {
  const loadExpenses = useCallback(() => listExpenses(from, to), [from, to])
  const { data, loading, error, reload } = useLoader(loadExpenses)
  const { data: categories, error: categoriesError } = useLoader(listExpenseCategories)
  const [editing, setEditing] = useState<Expense | 'new' | null>(presetOrderCode ? 'new' : null)
  const [confirmingDelete, setConfirmingDelete] = useState<number>()
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string }>()
  const [busy, setBusy] = useState(false)

  const [today] = useState(() => new Date().toLocaleDateString('en-CA', { timeZone: 'America/Sao_Paulo' }))
  const defaultDate = today >= from && today <= to ? today : from

  async function remove(expense: Expense) {
    setBusy(true)
    setNotice(undefined)
    try {
      await deleteExpense(expense)
      setConfirmingDelete(undefined)
      setNotice({ tone: 'success', text: `Despesa “${expense.description}” excluída.` })
      await reload()
    } catch (err) {
      const failure = describeSaveError(err)
      setNotice({ tone: 'error', text: failure.message ?? 'Não foi possível excluir.' })
      if (failure.stale) await reload()
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className={panel.section} aria-labelledby="expenses-title">
      <div className={panel.toolbar}>
        <h2 id="expenses-title">Despesas do período</h2>
        {editing === null && (
          <Button onClick={() => { setEditing('new'); setNotice(undefined) }} disabled={!categories}>
            Adicionar despesa
          </Button>
        )}
      </div>

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
      {categoriesError && <Alert tone="error">{categoriesError}</Alert>}
      {editing !== null && categories && (
        <ExpenseForm
          key={editing === 'new' ? 'new' : editing.id}
          expense={editing === 'new' ? undefined : editing}
          categories={categories}
          defaultDate={defaultDate}
          presetOrderCode={presetOrderCode}
          onSaved={(saved) => {
            setEditing(null)
            setNotice({ tone: 'success', text: `Despesa “${saved.description}” salva.` })
            void reload()
          }}
          onCancel={() => setEditing(null)}
          onStale={() => void reload()}
        />
      )}

      {loading && !data && <p role="status">Carregando…</p>}
      {error && <Alert tone="error">{error}</Alert>}
      {data && data.content.length === 0 && <p className={panel.empty}>Nenhuma despesa registrada neste período.</p>}
      {data && data.content.length > 0 && (
        <ul className={panel.list}>
          {data.content.map((expense) => (
            <li key={expense.id} className={panel.card}>
              <div className={panel.cardHeader}>
                <div>
                  <h3 className={panel.cardTitle}>{expense.description}</h3>
                  <ul className={panel.meta}>
                    <li>{formatDate(expense.expenseDate)}</li>
                    <li>{expense.categoryName}</li>
                    {expense.order && (
                      <li>
                        Pedido <Link to={`/admin/pedidos/${expense.order.id}`}>{expense.order.customerName}</Link>
                      </li>
                    )}
                  </ul>
                  {expense.notes && <p className={panel.description}>{expense.notes}</p>}
                </div>
                <span className={panel.price}>{formatBRL(expense.amount)}</span>
              </div>
              {confirmingDelete === expense.id ? (
                <div className={panel.actions}>
                  <span>Excluir esta despesa? Ela sai dos totais, mas fica registrada no histórico do sistema.</span>
                  <Button loading={busy} onClick={() => void remove(expense)}>Confirmar exclusão</Button>
                  <Button variant="secondary" disabled={busy} onClick={() => setConfirmingDelete(undefined)}>Voltar</Button>
                </div>
              ) : (
                <div className={panel.actions}>
                  <Button variant="secondary" disabled={editing !== null}
                    onClick={() => { setEditing(expense); setNotice(undefined) }}>
                    Editar
                  </Button>
                  <Button variant="secondary" onClick={() => setConfirmingDelete(expense.id)}>Excluir</Button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      {data && data.totalPages > 1 && (
        <p className={panel.description}>
          Mostrando as {data.content.length} despesas mais recentes de {data.totalElements}.
        </p>
      )}
    </section>
  )
}
