import { useState } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { useLoader } from '../../../hooks/useLoader'
import { listSizes, setSizeActive } from '../../../services/catalog'
import type { CakeSize } from '../../../types/catalog'
import { formatBRL } from '../../../utils/money'
import styles from '../panel.module.css'
import { describeSaveError } from '../saveErrors'
import { SizeForm } from './SizeForm'
import { Loading } from '../../../components/Loading'

function sortSizes(sizes: CakeSize[]): CakeSize[] {
  return [...sizes].sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name, 'pt-BR'))
}

function plural(count: number, singular: string, pluralForm: string) {
  return `${count} ${count === 1 ? singular : pluralForm}`
}

export function SizesSection() {
  const { data: sizes, loading, error, reload, setData } = useLoader(listSizes)
  const [editing, setEditing] = useState<CakeSize | 'new' | null>(null)
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string }>()
  const [togglingId, setTogglingId] = useState<number>()

  function replace(saved: CakeSize) {
    setData((current) => sortSizes([...(current ?? []).filter((s) => s.id !== saved.id), saved]))
  }

  function handleSaved(saved: CakeSize) {
    replace(saved)
    setEditing(null)
    setNotice({ tone: 'success', text: `Tamanho "${saved.name}" salvo.` })
  }

  async function toggle(size: CakeSize) {
    setTogglingId(size.id)
    setNotice(undefined)
    try {
      const saved = await setSizeActive(size, !size.active)
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

  return (
    <section className={styles.section} aria-labelledby="sizes-title">
      <div className={styles.toolbar}>
        <h2 id="sizes-title">Tamanhos</h2>
        {editing === null && (
          <Button onClick={() => { setEditing('new'); setNotice(undefined) }}>Adicionar tamanho</Button>
        )}
      </div>

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
      {editing !== null && (
        <SizeForm
          key={editing === 'new' ? 'new' : editing.id}
          size={editing === 'new' ? undefined : editing}
          onSaved={handleSaved}
          onCancel={() => setEditing(null)}
          onStale={() => void reload()}
        />
      )}

      {loading && <Loading />}
      {error && <Alert tone="error">{error}</Alert>}
      {sizes && sizes.length === 0 && (
        <p className={styles.empty}>Nenhum tamanho cadastrado ainda. Use “Adicionar tamanho” para começar.</p>
      )}
      {sizes && sizes.length > 0 && (
        <ul className={styles.list}>
          {sizes.map((size) => (
            <li key={size.id} className={`${styles.card} ${size.active ? '' : styles.inactive}`}>
              <div className={styles.cardHeader}>
                <div>
                  <h3 className={styles.cardTitle}>
                    {size.name} {!size.active && <span className={styles.badge}>Inativo</span>}
                  </h3>
                  {size.description && <p className={styles.description}>{size.description}</p>}
                </div>
                <span className={styles.price}>a partir de {formatBRL(size.basePrice)}</span>
              </div>
              <ul className={styles.meta}>
                {size.servings != null && <li>Serve {plural(size.servings, 'pessoa', 'pessoas')}</li>}
                <li>Até {plural(size.maxFlavors, 'massa', 'massas')}</li>
                <li>Até {plural(size.maxFillings, 'recheio', 'recheios')}</li>
                <li>Ordem: {size.displayOrder}</li>
              </ul>
              <div className={styles.actions}>
                <Button variant="secondary" onClick={() => { setEditing(size); setNotice(undefined) }} disabled={editing !== null}>
                  Editar
                </Button>
                <Button variant="secondary" onClick={() => void toggle(size)} loading={togglingId === size.id}>
                  {size.active ? 'Desativar' : 'Ativar'}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
