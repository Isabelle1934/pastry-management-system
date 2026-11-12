import { useId } from 'react'
import { Icon } from '../../../../components/Icon'
import { ProductCard } from '../../../../components/ProductCard'
import { setQuantity } from '../../../../utils/cart'
import type { StepProps } from './stepProps'
import styles from '../order.module.css'

/** Escolha do que a cliente deseja: bolo personalizado (sob orçamento) e/ou produtos do catálogo. */
export function StepItems({ form, errors, update, catalog }: StepProps) {
  const titleId = useId()
  const cakeOffered = catalog.customCakeEnabled || form.customCake
  const quantityOf = (id: number) => form.items.find((i) => i.productId === id)?.quantity ?? 0

  return (
    <section className={styles.card} aria-labelledby={titleId}>
      <h2 id={titleId}>O que você deseja?</h2>
      <p className={styles.hint}>
        Escolha produtos do cardápio e, se quiser, um bolo personalizado. Tudo é confirmado no orçamento antes de
        qualquer pagamento.
      </p>

      {cakeOffered && (
        <label className={`${styles.choice} ${styles.cakeToggle}`}>
          <input
            type="checkbox"
            name="customCake"
            checked={form.customCake}
            onChange={() => update({ customCake: !form.customCake })}
          />
          <span className={styles.choiceTitle}>
            <Icon name="cake" size={18} /> Quero um bolo personalizado
          </span>
          <span className={styles.choiceMeta}>
            Você escolhe tamanho, massa, recheio e decoração. Preço sob orçamento.
          </span>
        </label>
      )}

      {catalog.categories.map((category) => {
        const products = catalog.products.filter((p) => p.categoryId === category.id)
        if (products.length === 0) return null
        return (
          <fieldset key={category.id} className={styles.fieldset}>
            <legend className={styles.legend}>{category.name}</legend>
            {category.description && <p className={styles.hint}>{category.description}</p>}
            <div className={styles.productGrid}>
              {products.map((product) => (
                <ProductCard
                  key={product.id}
                  product={product}
                  quantity={quantityOf(product.id)}
                  onQuantityChange={(quantity) => update({ items: setQuantity(form.items, product.id, quantity) })}
                />
              ))}
            </div>
          </fieldset>
        )
      })}

      {errors.items && (
        <p className={styles.error} role="alert">
          {errors.items}
        </p>
      )}
      {errors.customCake && (
        <p className={styles.error} role="alert">
          {errors.customCake}
        </p>
      )}
    </section>
  )
}
