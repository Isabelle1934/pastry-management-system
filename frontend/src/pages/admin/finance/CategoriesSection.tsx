import { useState, type FormEvent } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { TextField } from '../../../components/TextField'
import { useLoader } from '../../../hooks/useLoader'
import { listExpenseCategories, saveExpenseCategory, setExpenseCategoryActive } from '../../../services/finance'
import type { ExpenseCategory } from '../../../types/finance'
import panel from '../panel.module.css'
import { describeSaveError } from '../saveErrors'

function sortCategories(categories: ExpenseCategory[]): ExpenseCategory[] {
  return [...categories].sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'))
}

function CategoryForm({ category, onSaved, onCancel, onStale }: {
  category?: ExpenseCategory
  onSaved: (category: ExpenseCategory) => void
  onCancel: () => void
  onStale: () => void
}) {
  const [name, setName] = useState(category?.name ?? '')
  const [error, setError] = useState<string>()
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!name.trim()) {
      setError('Informe o nome.')
      return
    }
    setSaving(true)
    setError(undefined)
    try {
      onSaved(await saveExpenseCategory(name.trim(), category))
    } catch (err) {
      const failure = describeSaveError(err)
      setError(failure.fields.name ?? failure.message)
      if (failure.stale) onStale()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className={panel.formCard}>
      <h3>{category ? `Renomear “${category.name}”` : 'Nova categoria'}</h3>
      <form className={panel.form} onSubmit={handleSubmit} noValidate>
        <TextField label="Nome" value={name} maxLength={60} autoFocus error={error}
          onChange={(e) => setName(e.target.value)} />
        <div className={panel.actions}>
          <Button type="submit" loading={saving}>Salvar</Button>
          <Button variant="secondary" onClick={onCancel} disabled={saving}>Cancelar</Button>
        </div>
      </form>
    </div>
  )
}

export function CategoriesSection() {
  const { data, loading, error, reload, setData } = useLoader(listExpenseCategories)
  const [editing, setEditing] = useState<ExpenseCategory | 'new' | null>(null)
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string }>()
  const [togglingId, setTogglingId] = useState<number>()

  function replace(saved: ExpenseCategory) {
    setData((current) => sortCategories([...(current ?? []).filter((c) => c.id !== saved.id), saved]))
  }

  async function toggle(category: ExpenseCategory) {
    setTogglingId(category.id)
    setNotice(undefined)
    try {
      const saved = await setExpenseCategoryActive(category, !category.active)
      replace(saved)
      setNotice({
        tone: 'success',
        text: saved.active
          ? `“${saved.name}” voltou a aparecer nas despesas novas.`
          : `“${saved.name}” não aparece mais nas despesas novas (as antigas continuam com ela).`,
      })
    } catch (err) {
      const failure = describeSaveError(err)
      setNotice({ tone: 'error', text: failure.message ?? 'Não foi possível alterar.' })
      if (failure.stale) await reload()
    } finally {
      setTogglingId(undefined)
    }
  }

  return (
    <section className={panel.section} aria-labelledby="categories-title">
      <div className={panel.toolbar}>
        <h2 id="categories-title">Categorias de despesa</h2>
        {editing === null && (
          <Button onClick={() => { setEditing('new'); setNotice(undefined) }}>Adicionar categoria</Button>
        )}
      </div>

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
      {editing !== null && (
        <CategoryForm
          key={editing === 'new' ? 'new' : editing.id}
          category={editing === 'new' ? undefined : editing}
          onSaved={(saved) => {
            replace(saved)
            setEditing(null)
            setNotice({ tone: 'success', text: `“${saved.name}” salva.` })
          }}
          onCancel={() => setEditing(null)}
          onStale={() => void reload()}
        />
      )}

      {loading && !data && <p role="status">Carregando…</p>}
      {error && <Alert tone="error">{error}</Alert>}
      {data && data.length === 0 && <p className={panel.empty}>Nenhuma categoria cadastrada.</p>}
      {data && data.length > 0 && (
        <ul className={panel.list}>
          {data.map((category) => (
            <li key={category.id} className={`${panel.card} ${category.active ? '' : panel.inactive}`}>
              <h3 className={panel.cardTitle}>
                {category.name} {!category.active && <span className={panel.badge}>Inativa</span>}
              </h3>
              <div className={panel.actions}>
                <Button variant="secondary" disabled={editing !== null}
                  onClick={() => { setEditing(category); setNotice(undefined) }}>
                  Renomear
                </Button>
                <Button variant="secondary" loading={togglingId === category.id} onClick={() => void toggle(category)}>
                  {category.active ? 'Desativar' : 'Ativar'}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
