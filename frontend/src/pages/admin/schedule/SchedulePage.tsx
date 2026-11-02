import { useSearchParams } from 'react-router'
import panel from '../panel.module.css'
import { BlocksSection } from './BlocksSection'
import { CalendarSection } from './CalendarSection'
import { RulesSection } from './RulesSection'
import { WeeklySlotsSection } from './WeeklySlotsSection'

const TABS = [
  { id: 'visao', label: 'Próximos dias' },
  { id: 'horarios', label: 'Horários da semana' },
  { id: 'bloqueios', label: 'Bloqueios' },
  { id: 'regras', label: 'Regras' },
] as const

type TabId = (typeof TABS)[number]['id']

export function SchedulePage() {
  const [params, setParams] = useSearchParams()
  const current: TabId = TABS.some((t) => t.id === params.get('aba')) ? (params.get('aba') as TabId) : 'visao'

  return (
    <div className={panel.page}>
      <h1>Agenda</h1>
      <div className={panel.tabs} role="tablist" aria-label="Seções da agenda">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            id={`tab-${tab.id}`}
            aria-selected={current === tab.id}
            aria-controls={`panel-${tab.id}`}
            className={panel.tab}
            onClick={() => setParams({ aba: tab.id }, { replace: true })}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`panel-${current}`} aria-labelledby={`tab-${current}`}>
        {current === 'visao' && <CalendarSection />}
        {current === 'horarios' && <WeeklySlotsSection />}
        {current === 'bloqueios' && <BlocksSection />}
        {current === 'regras' && <RulesSection />}
      </div>
    </div>
  )
}
