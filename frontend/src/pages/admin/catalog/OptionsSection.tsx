import { useCallback, useState, type FormEvent } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { TextAreaField } from '../../../components/TextAreaField'
import { TextField } from '../../../components/TextField'
import { useLoader } from '../../../hooks/useLoader'
import { listOptions, saveOption, setOptionActive } from '../../../services/catalog'
import type { CatalogOption, OptionKind } from '../../../types/catalog'
import styles from '../panel.module.css'
import { describeSaveError } from '../saveErrors'
import { Loading } from '../../../components/Loading'

export interface OptionLabels {
  title: string
  singular: string
  add: string
  empty: string
}

function sortOptions(options: CatalogOption[]): CatalogOption[] {
  return [...options].sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name, 'pt-BR'))
}

interface OptionFormProps {
  kind: OptionKind
  labels: OptionLabels
  option?: CatalogOption
  onSaved: (option: CatalogOption) => void
  onCancel: () => void
  onStale: () => void
}

function OptionForm({ kind, labels, option, onSaved, onCancel, onStale }: OptionFormProps) {
  const [name, setName] = useState(option?.name ?? '')
  const [description, setDescription] = useState(option?.description ?? '')
  const [displayOrder, setDisplayOrder] = useState(String(option?.displayOrder ?? 0))
  const [errors, setErrors] = useState<Record<string, string | undefined>>({})
  const [formError, setFormError] = useState<string>()
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const order = /^\d+$/.test(displayOrder.trim()) ? Number(displayOrder) : null
    const nextErrors = {
      name: name.trim() ? undefined : 'Informe o nome.',
      displayOrder: order == null || order > 9999 ? 'Use um número de 0 a 9999.' : undefined,
    }
    setErrors(nextErrors)
    setFormError(undefined)
    if (nextErrors.name || nextErrors.displayOrder) return

    setSaving(true)
    try {
      onSaved(
        await saveOption(
          kind,
          { name: name.trim(), description: description.trim(), displayOrder: order!, version: option?.version },
          option?.id,
        ),
      )
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
      <h3>{option ? `Editar ${labels.singular} "${option.name}"` : labels.add}</h3>
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}
        <TextField label="Nome" value={name} onChange={(e) => setName(e.target.value)} error={errors.name} maxLength={80} autoFocus />
        <TextAreaField
          label="Descrição (opcional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          error={errors.description}
          maxLength={300}
          rows={2}
        />
        <TextField
          label="Ordem na lista"
          inputMode="numeric"
          value={displayOrder}
          onChange={(e) => setDisplayOrder(e.target.value)}
          error={errors.displayOrder}
          hint="Menor número aparece primeiro."
        />
        <div className={styles.actions}>
          <Button type="submit" loading={saving}>
            Salvar
          </Button>
          <Button variant="secondary" onClick={onCancel} disabled={saving}>
            Cancelar
          </Button>
        </div>
      </form>
    </div>
  )
}

/** Lista e cadastro de massas ou de recheios (mesma tela, rótulos diferentes). */
export function OptionsSection({ kind, labels }: { kind: OptionKind; labels: OptionLabels }) {
  const load = useCallback(() => listOptions(kind), [kind])
  const { data: options, loading, error, reload, setData } = useLoader(load)
  const [editing, setEditing] = useState<CatalogOption | 'new' | null>(null)
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string }>()
  const [togglingId, setTogglingId] = useState<number>()

  function replace(saved: CatalogOption) {
    setData((current) => sortOptions([...(current ?? []).filter((o) => o.id !== saved.id), saved]))
  }

  async function toggle(option: CatalogOption) {
    setTogglingId(option.id)
    setNotice(undefined)
    try {
      const saved = await setOptionActive(kind, option, !option.active)
      replace(saved)
      setNotice({
        tone: 'success',
        text: saved.active
          ? `"${saved.name}" voltou a aparecer no formulário de encomenda.`
          : `"${saved.name}" não aparece mais no formulário de encomenda.`,
      })
    } catch (err) {
      const failure = describeSaveError(err)
      setNotice({ tone: 'error', text: failure.message ?? 'Não foi possível alterar.' })
      if (failure.stale) await reload()
    } finally {
      setTogglingId(undefined)
    }
  }

  const titleId = `${kind}-title`
  return (
    <section className={styles.section} aria-labelledby={titleId}>
      <div className={styles.toolbar}>
        <h2 id={titleId}>{labels.title}</h2>
        {editing === null && (
          <Button onClick={() => { setEditing('new'); setNotice(undefined) }}>{labels.add}</Button>
        )}
      </div>

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
      {editing !== null && (
        <OptionForm
          key={editing === 'new' ? 'new' : editing.id}
          kind={kind}
          labels={labels}
          option={editing === 'new' ? undefined : editing}
          onSaved={(saved) => {
            replace(saved)
            setEditing(null)
            setNotice({ tone: 'success', text: `"${saved.name}" salvo.` })
          }}
          onCancel={() => setEditing(null)}
          onStale={() => void reload()}
        />
      )}

      {loading && <Loading />}
      {error && <Alert tone="error">{error}</Alert>}
      {options && options.length === 0 && <p className={styles.empty}>{labels.empty}</p>}
      {options && options.length > 0 && (
        <ul className={styles.list}>
          {options.map((option) => (
            <li key={option.id} className={`${styles.card} ${option.active ? '' : styles.inactive}`}>
              <div>
                <h3 className={styles.cardTitle}>
                  {option.name} {!option.active && <span className={styles.badge}>Inativo</span>}
                </h3>
                {option.description && <p className={styles.description}>{option.description}</p>}
              </div>
              <div className={styles.actions}>
                <Button variant="secondary" onClick={() => { setEditing(option); setNotice(undefined) }} disabled={editing !== null}>
                  Editar
                </Button>
                <Button variant="secondary" onClick={() => void toggle(option)} loading={togglingId === option.id}>
                  {option.active ? 'Desativar' : 'Ativar'}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
