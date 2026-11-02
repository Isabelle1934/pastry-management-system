import { useSearchParams } from 'react-router'
import { Button } from '../../../components/Button'
import { currentMonth, formatMonth, isMonthKey, monthRange, shiftMonth } from '../../../utils/period'
import panel from '../panel.module.css'
import { CategoriesSection } from './CategoriesSection'
import { ExpensesSection } from './ExpensesSection'
import styles from './finance.module.css'
import { SummarySection } from './SummarySection'

const TABS = [
  { id: 'resumo', label: 'Resumo' },
  { id: 'despesas', label: 'Despesas' },
  { id: 'categorias', label: 'Categorias' },
] as const

type TabId = (typeof TABS)[number]['id']

export function FinancePage() {
  const [params, setParams] = useSearchParams()
  const month = isMonthKey(params.get('mes')) ? params.get('mes')! : currentMonth()
  const tab: TabId = TABS.some((t) => t.id === params.get('aba')) ? (params.get('aba') as TabId) : 'resumo'
  const presetOrderCode = params.get('pedido') ?? undefined
  const { from, to } = monthRange(month)

  function update(next: { mes?: string; aba?: TabId }) {
    const values: Record<string, string> = { mes: next.mes ?? month, aba: next.aba ?? tab }
    setParams(values, { replace: true })
  }

  return (
    <div className={panel.page}>
      <h1>Financeiro</h1>
      <p className={panel.intro}>Faturamento, recebimentos, valores a receber e despesas do mês.</p>

      {tab !== 'categorias' && (
        <div className={styles.monthNav}>
          <Button variant="secondary" onClick={() => update({ mes: shiftMonth(month, -1) })}
            aria-label="Mês anterior">‹</Button>
          <h2 className={styles.monthTitle} aria-live="polite">{formatMonth(month)}</h2>
          <Button variant="secondary" onClick={() => update({ mes: shiftMonth(month, 1) })}
            aria-label="Próximo mês">›</Button>
          {month !== currentMonth() && (
            <Button variant="secondary" onClick={() => update({ mes: currentMonth() })}>Mês atual</Button>
          )}
        </div>
      )}

      <div className={panel.tabs} role="tablist" aria-label="Seção do financeiro">
        {TABS.map((item) => (
          <button key={item.id} type="button" role="tab" id={`tab-${item.id}`} aria-selected={tab === item.id}
            aria-controls={`panel-${item.id}`} className={panel.tab} onClick={() => update({ aba: item.id })}>
            {item.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`}>
        {tab === 'resumo' && <SummarySection key={month} from={from} to={to} />}
        {tab === 'despesas' && (
          <ExpensesSection key={month} from={from} to={to} presetOrderCode={presetOrderCode} />
        )}
        {tab === 'categorias' && <CategoriesSection />}
      </div>
    </div>
  )
}
