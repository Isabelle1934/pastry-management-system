import { useSearchParams } from 'react-router'
import { Button } from '../../../components/Button'
import { currentMonth, formatMonth, isMonthKey, monthRange, shiftMonth } from '../../../utils/period'
import panel from '../panel.module.css'
import styles from './finance.module.css'
import { SummarySection } from './SummarySection'

/** Indicadores financeiros do mês: a receber, recebido, lucro e faturamento previsto. */
export function FinancePage() {
  const [params, setParams] = useSearchParams()
  const month = isMonthKey(params.get('mes')) ? params.get('mes')! : currentMonth()
  const { from, to } = monthRange(month)

  function goTo(mes: string) {
    setParams({ mes }, { replace: true })
  }

  return (
    <div className={panel.page}>
      <h1>Financeiro</h1>
      <p className={panel.intro}>A receber, recebimentos, lucro e faturamento previsto do mês.</p>

      <div className={styles.monthNav}>
        <Button variant="secondary" onClick={() => goTo(shiftMonth(month, -1))} aria-label="Mês anterior">‹</Button>
        <h2 className={styles.monthTitle} aria-live="polite">{formatMonth(month)}</h2>
        <Button variant="secondary" onClick={() => goTo(shiftMonth(month, 1))} aria-label="Próximo mês">›</Button>
        {month !== currentMonth() && (
          <Button variant="secondary" onClick={() => goTo(currentMonth())}>Mês atual</Button>
        )}
      </div>

      <SummarySection key={month} from={from} to={to} />
    </div>
  )
}
