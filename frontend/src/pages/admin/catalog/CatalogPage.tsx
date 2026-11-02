import { useSearchParams } from 'react-router'
import styles from './catalog.module.css'
import { OptionsSection, type OptionLabels } from './OptionsSection'
import { SizesSection } from './SizesSection'

const TABS = [
  { id: 'tamanhos', label: 'Tamanhos' },
  { id: 'massas', label: 'Massas' },
  { id: 'recheios', label: 'Recheios' },
] as const

type TabId = (typeof TABS)[number]['id']

const FLAVOR_LABELS: OptionLabels = {
  title: 'Massas',
  singular: 'massa',
  add: 'Adicionar massa',
  empty: 'Nenhuma massa cadastrada ainda. Use “Adicionar massa” para começar.',
}

const FILLING_LABELS: OptionLabels = {
  title: 'Recheios',
  singular: 'recheio',
  add: 'Adicionar recheio',
  empty: 'Nenhum recheio cadastrado ainda. Use “Adicionar recheio” para começar.',
}

export function CatalogPage() {
  const [params, setParams] = useSearchParams()
  const current: TabId = TABS.some((t) => t.id === params.get('aba')) ? (params.get('aba') as TabId) : 'tamanhos'

  return (
    <div className={styles.page}>
      <h1>Produtos</h1>
      <p className={styles.intro}>
        O que você cadastrar aqui aparece como opção no formulário de encomenda. Itens desativados deixam de aparecer,
        mas continuam nos pedidos antigos.
      </p>
      <div className={styles.tabs} role="tablist" aria-label="Tipo de item">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            id={`tab-${tab.id}`}
            aria-selected={current === tab.id}
            aria-controls={`panel-${tab.id}`}
            className={styles.tab}
            onClick={() => setParams({ aba: tab.id }, { replace: true })}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`panel-${current}`} aria-labelledby={`tab-${current}`}>
        {current === 'tamanhos' && <SizesSection />}
        {current === 'massas' && <OptionsSection key="flavors" kind="flavors" labels={FLAVOR_LABELS} />}
        {current === 'recheios' && <OptionsSection key="fillings" kind="fillings" labels={FILLING_LABELS} />}
      </div>
    </div>
  )
}
