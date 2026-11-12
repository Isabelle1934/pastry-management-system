import { publicPhotoUrl } from '../services/products'
import type { PublicProduct } from '../types/product'
import { availabilityLabel, priceLabel } from '../utils/productRules'
import { Icon } from './Icon'
import styles from './ProductCard.module.css'

interface ProductCardProps {
  product: PublicProduct
  /** Quantidade escolhida (0 = não escolhido). Sem `onQuantityChange`, o cartão é só de exibição. */
  quantity?: number
  onQuantityChange?: (quantity: number) => void
}

/** Cartão de produto do catálogo público: foto, nome, preço, disponibilidade e quantidade. */
export function ProductCard({ product, quantity = 0, onQuantityChange }: ProductCardProps) {
  const photoId = product.photoIds[0]
  const availability = availabilityLabel(product)
  const selectable = product.available && onQuantityChange != null
  const min = product.minQuantity
  const inputId = `qty-${product.id}`

  return (
    <article className={`${styles.card} ${quantity > 0 ? styles.selected : ''} ${!product.available ? styles.muted : ''}`}>
      <div className={styles.media}>
        {photoId != null ? (
          <img src={publicPhotoUrl(product.id, photoId)} alt={product.name} loading="lazy" decoding="async" />
        ) : (
          <span className={styles.noPhoto} aria-hidden="true">
            <Icon name="cake" size={30} />
          </span>
        )}
        <div className={styles.badges}>
          {product.seasonal && <span className={`${styles.badge} ${styles.special}`}>Especial</span>}
          {!product.available && product.orderable && <span className={styles.badge}>Indisponível no momento</span>}
          {!product.orderable && <span className={styles.badge}>Somente na loja</span>}
        </div>
      </div>
      <div className={styles.body}>
        <h3 className={styles.name}>{product.name}</h3>
        {product.description && <p className={styles.description}>{product.description}</p>}
        <p className={styles.price}>{priceLabel(product.price, product.unitLabel)}</p>
        <ul className={styles.meta}>
          {min > 1 && <li>Pedido mínimo: {min}</li>}
          {availability && <li>{availability}</li>}
          {product.deliveryAllowed && (
            <li>
              <Icon name="truck" size={14} /> Aceita delivery
            </li>
          )}
        </ul>
        {selectable && (
          <div className={styles.actions}>
            {quantity === 0 ? (
              <button type="button" className={styles.add} onClick={() => onQuantityChange(min)}>
                <Icon name="plus" size={16} /> Adicionar
              </button>
            ) : (
              <div className={styles.stepper} role="group" aria-label={`Quantidade de ${product.name}`}>
                <button
                  type="button"
                  className={styles.stepButton}
                  aria-label={quantity <= min ? `Remover ${product.name}` : 'Diminuir'}
                  onClick={() => onQuantityChange(quantity <= min ? 0 : quantity - 1)}
                >
                  <Icon name="minus" size={16} />
                </button>
                <label htmlFor={inputId} className={styles.srOnly}>
                  Quantidade de {product.name}
                </label>
                <input
                  id={inputId}
                  className={styles.qty}
                  inputMode="numeric"
                  value={quantity}
                  onChange={(e) => {
                    const value = Number(e.target.value.replace(/\D/g, '').slice(0, 4))
                    onQuantityChange(Number.isFinite(value) ? value : 0)
                  }}
                />
                <button
                  type="button"
                  className={styles.stepButton}
                  aria-label="Aumentar"
                  onClick={() => onQuantityChange(Math.min(quantity + 1, 9999))}
                >
                  <Icon name="plus" size={16} />
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </article>
  )
}
