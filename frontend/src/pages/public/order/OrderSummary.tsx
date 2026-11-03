import { Icon } from '../../../components/Icon'
import type { PublicCatalog } from '../../../types/public'
import { formatLongDay } from '../../../utils/date'
import { formatBRL } from '../../../utils/money'
import type { OrderFormState } from './orderForm'
import styles from './order.module.css'

/** Resumo do que a cliente já escolheu, ao lado do formulário (telas largas). */
export function OrderSummary({ form, catalog }: { form: OrderFormState; catalog: PublicCatalog }) {
  const size = catalog.sizes.find((s) => s.id === form.sizeId)
  const names = (ids: number[], options: { id: number; name: string }[]) =>
    ids.map((id) => options.find((o) => o.id === id)?.name).filter(Boolean).join(', ')
  const rows = [
    { label: 'Retirada', value: form.pickupDate && `${formatLongDay(form.pickupDate)}${form.pickupTime ? ` às ${form.pickupTime}` : ''}` },
    { label: 'Tamanho', value: size && `${size.name}${size.servings != null ? ` · serve ${size.servings}` : ''}` },
    { label: 'Massa', value: names(form.flavorIds, catalog.flavors) },
    { label: 'Recheio', value: names(form.fillingIds, catalog.fillings) },
    { label: 'Topo', value: form.topperRequested ? form.topperTheme.trim() || 'Sim' : '' },
  ]
  const filled = rows.filter((row) => row.value)

  return (
    <aside className={styles.summary} aria-label="Resumo da encomenda">
      <p className={styles.summaryEyebrow}><Icon name="cake" size={18} /> Sua encomenda</p>
      {filled.length === 0 ? (
        <p className={styles.hint}>Suas escolhas aparecem aqui conforme você avança.</p>
      ) : (
        <dl className={styles.summaryList}>
          {filled.map((row) => (
            <div key={row.label} className={styles.summaryRow}>
              <dt>{row.label}</dt>
              <dd>{row.value}</dd>
            </div>
          ))}
        </dl>
      )}
      {size && (
        <p className={styles.summaryPrice}>
          <span>A partir de</span>
          <strong>{formatBRL(size.basePrice)}</strong>
        </p>
      )}
      <p className={styles.hint}>O valor final é definido no orçamento personalizado.</p>
    </aside>
  )
}
