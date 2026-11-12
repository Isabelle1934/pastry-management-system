import { useState, type FormEvent } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { Loading } from '../../../components/Loading'
import { TextField } from '../../../components/TextField'
import { useLoader } from '../../../hooks/useLoader'
import { listCategories, saveCategory, setCategoryActive } from '../../../services/products'
import type { ProductCategory } from '../../../types/product'
import panel from '../panel.module.css'
import { describeSaveError } from '../saveErrors'

function sortCategories(list: ProductCategory[]): ProductCategory[] {
  return [...list].sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name, 'pt-BR'))
}

function CategoryForm({ category, onSaved, onCancel, onStale }: {
  category?: ProductCategory
  onSaved: (c: ProductCategory) => void
  onCancel: () => void
  onStale: () => void
}) {
  const [name, setName] = useState(category?.name ?? '')
  const [description, setDescription] = useState(category?.description ?? '')
  const [displayOrder, setDisplayOrder] = useState(String(category?.displayOrder ?? 0))
  const [errors, setErrors] = useState<Record<string, string | undefined>>({})
  const [formError, setFormError] = useState<string>()
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const order = /^\d+$/.test(displayOrder.trim()) && Number(displayOrder) <= 9999 ? Number(displayOrder) : null
    const nextErrors = {
      name: name.trim() ? undefined : 'Informe o nome.',
      displayOrder: order == null ? 'Use um número de 0 a 9999.' : undefined,
    }
    setErrors(nextErrors)
    setFormError(undefined)
    if (Object.values(nextErrors).some(Boolean)) return
    setSaving(true)
    try {
      onSaved(await saveCategory({ name: name.trim(), description: description.trim(), displayOrder: order!,
        version: category?.version }, category?.id))
    } catch (error) {
      const failure = describeSaveError(error)
      setErrors(failure.fields)
      setFormError(failure.message)
      if (failure.stale) onStale()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className={panel.formCard}>
      <h3>{category ? `Editar categoria "${category.name}"` : 'Nova categoria'}</h3>
      <form className={panel.form} onSubmit={handleSubmit} noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}
        <TextField label="Nome" placeholder="Ex.: Brigadeiros, Kits e caixas" value={name}
          onChange={(e) => setName(e.target.value)} error={errors.name} maxLength={60} autoFocus />
        <TextField label="Descrição (opcional)" value={description} onChange={(e) => setDescription(e.target.value)}
          maxLength={300} />
        <TextField label="Ordem de exibição" inputMode="numeric" value={displayOrder}
          onChange={(e) => setDisplayOrder(e.target.value)} error={errors.displayOrder} />
        <div className={panel.actions}>
          <Button type="submit" loading={saving}>Salvar</Button>
          <Button variant="secondary" onClick={onCancel} disabled={saving}>Cancelar</Button>
        </div>
      </form>
    </div>
  )
}

export function CategoriesSection() {
  const { data, loading, error, reload, setData } = useLoader(listCategories)
  const [editing, setEditing] = useState<ProductCategory | 'new' | null>(null)
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string }>()
  const [busyId, setBusyId] = useState<number>()

  function replace(saved: ProductCategory) {
    setData((current) => sortCategories([...(current ?? []).filter((c) => c.id !== saved.id), saved]))
  }

  async function toggle(category: ProductCategory) {
    setBusyId(category.id)
    setNotice(undefined)
    try {
      const saved = await setCategoryActive(category, !category.active)
      replace(saved)
      setNotice({
        tone: 'success',
        text: saved.active
          ? `"${saved.name}" voltou ao catálogo público.`
          : `"${saved.name}" e seus produtos saíram do catálogo público.`,
      })
    } catch (err) {
      const failure = describeSaveError(err)
      setNotice({ tone: 'error', text: failure.message ?? 'Não foi possível alterar.' })
      if (failure.stale) await reload()
    } finally {
      setBusyId(undefined)
    }
  }

  return (
    <section className={panel.section} aria-labelledby="categories-title">
      <div className={panel.toolbar}>
        <h2 id="categories-title">Categorias</h2>
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
            setNotice({ tone: 'success', text: `Categoria "${saved.name}" salva.` })
          }}
          onCancel={() => setEditing(null)}
          onStale={() => void reload()}
        />
      )}
      {loading && <Loading />}
      {error && <Alert tone="error">{error}</Alert>}
      {data && data.length === 0 && (
        <p className={panel.empty}>Nenhuma categoria ainda. Exemplos: Bolos tradicionais, Brigadeiros, Kits e caixas.</p>
      )}
      {data && data.length > 0 && (
        <ul className={panel.list}>
          {data.map((category) => (
            <li key={category.id} className={`${panel.card} ${category.active ? '' : panel.inactive}`}>
              <div className={panel.cardHeader}>
                <div>
                  <h3 className={panel.cardTitle}>
                    {category.name} {!category.active && <span className={panel.badge}>Inativa</span>}
                  </h3>
                  {category.description && <p className={panel.description}>{category.description}</p>}
                </div>
              </div>
              <ul className={panel.meta}>
                <li>{category.productCount} {category.productCount === 1 ? 'produto' : 'produtos'}</li>
                <li>Ordem: {category.displayOrder}</li>
              </ul>
              <div className={panel.actions}>
                <Button variant="secondary" disabled={editing !== null}
                  onClick={() => { setEditing(category); setNotice(undefined) }}>
                  Editar
                </Button>
                <Button variant="secondary" loading={busyId === category.id} onClick={() => void toggle(category)}>
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
