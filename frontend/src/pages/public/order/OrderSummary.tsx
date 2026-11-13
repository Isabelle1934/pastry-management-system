import { Icon } from '../../../components/Icon'
import type { PublicCatalog } from '../../../types/public'
import { formatLongDay } from '../../../utils/date'
import { formatBRL, fromCents, toCents } from '../../../utils/money'
import { FULFILLMENT_LABELS } from '../../../utils/productRules'
import type { OrderFormState } from './orderForm'
import styles from './order.module.css'

/** Resumo do que a cliente já escolheu, ao lado do formulário (telas largas). */
export function OrderSummary({ form, catalog }: { form: OrderFormState; catalog: PublicCatalog }) {
  const size = form.customCake ? catalog.sizes.find((s) => s.id === form.sizeId) : undefined
  const names = (ids: number[], options: { id: number; name: string }[]) =>
    ids.map((id) => options.find((o) => o.id === id)?.name).filter(Boolean).join(', ')
  const chosen = form.items
    .map((item) => ({ item, product: catalog.products.find((p) => p.id === item.productId) }))
    .filter((entry) => entry.product)
  const rows = [
    ...chosen.map(({ item, product }) => ({
      label: `${item.quantity} ×`,
      value: product?.price != null
        ? `${product.name} — ${formatBRL(fromCents(toCents(product.price) * item.quantity))}`
        : product?.name,
    })),
    { label: 'Bolo', value: size && `${size.name}${size.servings != null ? ` · serve ${size.servings}` : ''}` },
    { label: 'Massa', value: form.customCake ? names(form.flavorIds, catalog.flavors) : '' },
    { label: 'Recheio', value: form.customCake ? names(form.fillingIds, catalog.fillings) : '' },
    { label: 'Topo', value: form.customCake && form.topperRequested ? form.topperTheme.trim() || 'Sim' : '' },
    {
      label: form.fulfillmentType === 'DELIVERY' ? 'Entrega' : 'Retirada',
      value: form.pickupDate && `${formatLongDay(form.pickupDate)}${form.pickupTime ? ` às ${form.pickupTime}` : ''}`,
    },
  ]
  const filled = rows.filter((row) => row.value)
  // Estimativa só dos itens com preço definido; o valor final vem do orçamento.
  const priced = chosen.filter(({ product }) => product?.price != null)
  const estimate = priced.reduce((sum, { item, product }) => sum + toCents(product?.price ?? '0') * item.quantity, 0)
  const partial = priced.length < chosen.length || form.customCake

  return (
    <aside className={styles.summary} aria-label="Resumo da encomenda">
      <p className={styles.summaryEyebrow}><Icon name="cake" size={18} /> Sua encomenda</p>
      {filled.length === 0 ? (
        <p className={styles.hint}>Suas escolhas aparecem aqui conforme você avança.</p>
      ) : (
        <dl className={styles.summaryList}>
          {filled.map((row, index) => (
            <div key={`${row.label}-${index}`} className={styles.summaryRow}>
              <dt>{row.label}</dt>
              <dd>{row.value}</dd>
            </div>
          ))}
        </dl>
      )}
      {priced.length > 0 && (
        <p className={styles.summaryPrice}>
          <span>{partial ? 'Produtos com preço' : 'Estimativa'}</span>
          <strong>{formatBRL(fromCents(estimate))}</strong>
        </p>
      )}
      {size && !priced.length && (
        <p className={styles.summaryPrice}>
          <span>Bolo a partir de</span>
          <strong>{formatBRL(size.basePrice)}</strong>
        </p>
      )}
      {form.pickupDate && <p className={styles.hint}>{FULFILLMENT_LABELS[form.fulfillmentType]}</p>}
      <p className={styles.hint}>O valor final é definido no orçamento personalizado.</p>
    </aside>
  )
}
