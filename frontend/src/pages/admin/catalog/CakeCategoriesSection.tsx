import { useState, type FormEvent } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { Loading } from '../../../components/Loading'
import { TextAreaField } from '../../../components/TextAreaField'
import { TextField } from '../../../components/TextField'
import { useLoader } from '../../../hooks/useLoader'
import { listCakeCategories, saveCakeCategory, setCakeCategoryActive } from '../../../services/catalog'
import type { CakeCategory } from '../../../types/catalog'
import styles from '../panel.module.css'
import { describeSaveError } from '../saveErrors'

type Field = 'name' | 'description' | 'notice' | 'consultOnly' | 'maxFlavors' | 'maxFillings' | 'displayOrder'

function parseInteger(value: string, min: number, max: number): number | null {
  if (!/^\d+$/.test(value.trim())) return null
  const number = Number(value)
  return number >= min && number <= max ? number : null
}

function CakeCategoryForm({ category, onSaved, onCancel, onStale }: {
  category?: CakeCategory
  onSaved: (saved: CakeCategory) => void
  onCancel: () => void
  onStale: () => void
}) {
  const [name, setName] = useState(category?.name ?? '')
  const [description, setDescription] = useState(category?.description ?? '')
  const [notice, setNotice] = useState(category?.notice ?? '')
  const [consultOnly, setConsultOnly] = useState(category?.consultOnly ?? false)
  const [maxFlavors, setMaxFlavors] = useState(String(category?.maxFlavors ?? 1))
  const [maxFillings, setMaxFillings] = useState(String(category?.maxFillings ?? 2))
  const [displayOrder, setDisplayOrder] = useState(String(category?.displayOrder ?? 0))
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({})
  const [formError, setFormError] = useState<string>()
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const flavors = parseInteger(maxFlavors, 1, 10)
    const fillings = parseInteger(maxFillings, 1, 10)
    const order = parseInteger(displayOrder, 0, 9999)
    const next: Partial<Record<Field, string>> = {
      name: name.trim() ? undefined : 'Informe o nome.',
      maxFlavors: flavors == null ? 'Use um número entre 1 e 10.' : undefined,
      maxFillings: fillings == null ? 'Use um número entre 1 e 10.' : undefined,
      displayOrder: order == null ? 'Use um número de 0 a 9999.' : undefined,
    }
    setErrors(next)
    setFormError(undefined)
    if (Object.values(next).some(Boolean)) return
    setSaving(true)
    try {
      onSaved(await saveCakeCategory({
        name: name.trim(), description: description.trim(), notice: notice.trim(), consultOnly,
        maxFlavors: flavors!, maxFillings: fillings!, displayOrder: order!, version: category?.version,
      }, category?.id))
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
    <div className={styles.formCard}>
      <h3>{category ? `Editar "${category.name}"` : 'Novo tipo de bolo'}</h3>
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}
        <TextField label="Nome" value={name} onChange={(e) => setName(e.target.value)} error={errors.name} maxLength={80}
          autoFocus placeholder="Ex.: Bolos altos" />
        <TextAreaField label="Características (aparecem para a cliente)" value={description}
          onChange={(e) => setDescription(e.target.value)} error={errors.description} maxLength={300} rows={2}
          hint="Ex.: 3 camadas de recheio e 4 camadas de massa." />
        <TextField label="Aviso em destaque (opcional)" value={notice} onChange={(e) => setNotice(e.target.value)}
          error={errors.notice} maxLength={300} hint='Ex.: "Topo não incluso."' />
        <label style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'flex-start', cursor: 'pointer' }}>
          <input type="checkbox" checked={consultOnly} onChange={(e) => setConsultOnly(e.target.checked)}
            style={{ marginTop: 4 }} />
          <span>
            <strong style={{ fontWeight: 600 }}>Consulte valores (sem tabela de tamanhos)</strong>
            <span style={{ display: 'block', color: 'var(--color-text-muted)', fontSize: 'var(--text-sm)' }}>
              A cliente descreve o bolo e o valor é definido no orçamento. Ex.: bolos de 3 andares ou mais.
            </span>
          </span>
        </label>
        {errors.consultOnly && <Alert tone="error">{errors.consultOnly}</Alert>}
        <div className={`${styles.row} ${styles.row3}`}>
          {consultOnly && (<>
            <TextField label="Massas que a cliente pode escolher" inputMode="numeric" value={maxFlavors}
              onChange={(e) => setMaxFlavors(e.target.value)} error={errors.maxFlavors} />
            <TextField label="Recheios que a cliente pode escolher" inputMode="numeric" value={maxFillings}
              onChange={(e) => setMaxFillings(e.target.value)} error={errors.maxFillings} />
          </>)}
          <TextField label="Ordem na lista" inputMode="numeric" value={displayOrder}
            onChange={(e) => setDisplayOrder(e.target.value)} error={errors.displayOrder} hint="Menor número aparece primeiro." />
        </div>
        <div className={styles.actions}>
          <Button type="submit" loading={saving}>Salvar</Button>
          <Button variant="secondary" onClick={onCancel} disabled={saving}>Cancelar</Button>
        </div>
      </form>
    </div>
  )
}

/** Tipos de bolo personalizado (baixos, altos, 2 andares, consulte valores...). */
export function CakeCategoriesSection() {
  const { data, loading, error, reload, setData } = useLoader(listCakeCategories)
  const [editing, setEditing] = useState<CakeCategory | 'new' | null>(null)
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string }>()
  const [togglingId, setTogglingId] = useState<number>()

  function replace(saved: CakeCategory) {
    setData((current) => [...(current ?? []).filter((c) => c.id !== saved.id), saved]
      .sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name, 'pt-BR')))
  }

  async function toggle(category: CakeCategory) {
    setTogglingId(category.id)
    setNotice(undefined)
    try {
      const saved = await setCakeCategoryActive(category, !category.active)
      replace(saved)
      setNotice({ tone: 'success', text: saved.active ? `"${saved.name}" voltou para o site.`
        : `"${saved.name}" e seus tamanhos não aparecem mais no site.` })
    } catch (err) {
      const failure = describeSaveError(err)
      setNotice({ tone: 'error', text: failure.message ?? 'Não foi possível alterar.' })
      if (failure.stale) await reload()
    } finally {
      setTogglingId(undefined)
    }
  }

  return (
    <section className={styles.section} aria-labelledby="cake-categories-title">
      <div className={styles.toolbar}>
        <h2 id="cake-categories-title">Tipos de bolo</h2>
        {editing === null && <Button onClick={() => { setEditing('new'); setNotice(undefined) }}>Adicionar tipo</Button>}
      </div>
      <p className={styles.description}>
        Os tamanhos e preços de cada tipo ficam em "Bolo: tamanhos". Nada é apagado: tipos desativados saem do site.
      </p>
      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
      {editing !== null && (
        <CakeCategoryForm key={editing === 'new' ? 'new' : editing.id} category={editing === 'new' ? undefined : editing}
          onSaved={(saved) => { replace(saved); setEditing(null); setNotice({ tone: 'success', text: `"${saved.name}" salvo.` }) }}
          onCancel={() => setEditing(null)} onStale={() => void reload()} />
      )}
      {loading && <Loading />}
      {error && <Alert tone="error">{error}</Alert>}
      {data && data.length === 0 && <p className={styles.empty}>Nenhum tipo de bolo cadastrado.</p>}
      {data && data.length > 0 && (
        <ul className={styles.list}>
          {data.map((category) => (
            <li key={category.id} className={`${styles.card} ${category.active ? '' : styles.inactive}`}>
              <div className={styles.cardHeader}>
                <div>
                  <h3 className={styles.cardTitle}>
                    {category.name} {!category.active && <span className={styles.badge}>Inativo</span>}
                  </h3>
                  {category.description && <p className={styles.description}>{category.description}</p>}
                  {category.notice && <p className={styles.description}><strong>{category.notice}</strong></p>}
                </div>
              </div>
              <ul className={styles.meta}>
                <li>{category.consultOnly ? 'Consulte valores (sem tabela)'
                  : `${category.sizeCount} ${category.sizeCount === 1 ? 'tamanho' : 'tamanhos'}`}</li>
                <li>Ordem: {category.displayOrder}</li>
              </ul>
              <div className={styles.actions}>
                <Button variant="secondary" onClick={() => { setEditing(category); setNotice(undefined) }}
                  disabled={editing !== null}>Editar</Button>
                <Button variant="secondary" onClick={() => void toggle(category)} loading={togglingId === category.id}>
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
