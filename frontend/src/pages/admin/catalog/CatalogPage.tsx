import { useSearchParams } from 'react-router'
import styles from '../panel.module.css'
import { CategoriesSection } from './CategoriesSection'
import { OptionsSection, type OptionLabels } from './OptionsSection'
import { ProductsSection } from './ProductsSection'
import { SizesSection } from './SizesSection'

const TABS = [
  { id: 'produtos', label: 'Produtos' },
  { id: 'categorias', label: 'Categorias' },
  { id: 'tamanhos', label: 'Bolo: tamanhos' },
  { id: 'massas', label: 'Bolo: massas' },
  { id: 'recheios', label: 'Bolo: recheios' },
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
  const current: TabId = TABS.some((t) => t.id === params.get('aba')) ? (params.get('aba') as TabId) : 'produtos'

  return (
    <div className={styles.page}>
      <h1>Catálogo</h1>
      <p className={styles.intro}>
        Produtos e categorias aparecem no cardápio do site; tamanhos, massas e recheios são as opções do bolo
        personalizado. Nada é apagado: itens desativados saem do site, mas os pedidos antigos continuam com os dados
        originais.
      </p>
      <div className={styles.tabs} role="tablist" aria-label="Parte do catálogo">
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
        {current === 'produtos' && <ProductsSection />}
        {current === 'categorias' && <CategoriesSection />}
        {current === 'tamanhos' && <SizesSection />}
        {current === 'massas' && <OptionsSection key="flavors" kind="flavors" labels={FLAVOR_LABELS} />}
        {current === 'recheios' && <OptionsSection key="fillings" kind="fillings" labels={FILLING_LABELS} />}
      </div>
    </div>
  )
}
