import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import { Alert } from '../../components/Alert'
import { Icon } from '../../components/Icon'
import { Loading } from '../../components/Loading'
import { ProductCard } from '../../components/ProductCard'
import { useLoader } from '../../hooks/useLoader'
import { PublicLayout } from '../../layouts/PublicLayout'
import { getCatalog } from '../../services/publicApi'
import { readCart, setQuantity, writeCart } from '../../utils/cart'
import styles from './menu.module.css'

/**
 * Cardápio público: produtos por categoria, com foto, preço e disponibilidade. A cliente escolhe as
 * quantidades e segue para a encomenda; o bolo personalizado leva direto ao formulário.
 */
export function MenuPage() {
  const { data: catalog, loading, error } = useLoader(getCatalog)
  const [cart, setCart] = useState(readCart)
  const [category, setCategory] = useState<number | 'all'>('all')

  const selected = useMemo(
    () => cart.filter((item) => catalog?.products.some((p) => p.id === item.productId && p.available)),
    [cart, catalog],
  )
  const totalUnits = selected.reduce((sum, item) => sum + item.quantity, 0)

  function change(productId: number, quantity: number) {
    setCart((current) => {
      const next = setQuantity(current, productId, quantity)
      writeCart(next)
      return next
    })
  }

  const categories = catalog?.categories ?? []
  const shown = category === 'all' ? categories : categories.filter((c) => c.id === category)
  const cakeOpen = catalog && catalog.customCakeEnabled && catalog.sizes.length > 0

  return (
    <PublicLayout wide>
      <div className={styles.page}>
        <header className={styles.header}>
          <p className={styles.eyebrow}>Cardápio</p>
          <h1>Nossos doces</h1>
          <p className={styles.lead}>
            Escolha os produtos e as quantidades. Na próxima etapa você informa a data e a forma de receber, e recebe um
            orçamento para confirmar com 50% de sinal.
          </p>
        </header>

        {loading && <Loading />}
        {error && <Alert tone="error">{error}</Alert>}

        {cakeOpen && (
          <Link to="/encomendar?bolo=1" className={styles.cakeBanner}>
            <span className={styles.cakeIcon}><Icon name="cake" size={26} /></span>
            <span>
              <strong>Bolo personalizado</strong>
              <span>Escolha tamanho, massa, recheio e decoração. Preço sob orçamento.</span>
            </span>
            <Icon name="arrowRight" size={20} />
          </Link>
        )}

        {catalog && catalog.products.length === 0 && (
          <p className={styles.empty}>
            O cardápio está sendo preparado.{' '}
            {cakeOpen ? <Link to="/encomendar">Faça sua encomenda de bolo personalizado</Link> : 'Volte em breve!'}
          </p>
        )}

        {categories.length > 1 && (
          <div className={styles.filters} role="group" aria-label="Filtrar por categoria">
            <button type="button" className={styles.filter} aria-pressed={category === 'all'} onClick={() => setCategory('all')}>
              Todos
            </button>
            {categories.map((c) => (
              <button key={c.id} type="button" className={styles.filter} aria-pressed={category === c.id}
                onClick={() => setCategory(c.id)}>
                {c.name}
              </button>
            ))}
          </div>
        )}

        {catalog && shown.map((c) => {
          const products = catalog.products.filter((p) => p.categoryId === c.id)
          if (products.length === 0) return null
          return (
            <section key={c.id} className={styles.category} aria-labelledby={`cat-${c.id}`}>
              <h2 id={`cat-${c.id}`}>{c.name}</h2>
              {c.description && <p className={styles.lead}>{c.description}</p>}
              <div className={styles.grid}>
                {products.map((product) => (
                  <ProductCard
                    key={product.id}
                    product={product}
                    quantity={cart.find((i) => i.productId === product.id)?.quantity ?? 0}
                    onQuantityChange={(quantity) => change(product.id, quantity)}
                  />
                ))}
              </div>
            </section>
          )
        })}

        {selected.length > 0 && (
          <div className={styles.bar} role="region" aria-label="Itens escolhidos">
            <span>
              <strong>{selected.length} {selected.length === 1 ? 'produto' : 'produtos'}</strong>
              <span className={styles.units}> · {totalUnits} {totalUnits === 1 ? 'unidade' : 'unidades'}</span>
            </span>
            <Link to="/encomendar" className={styles.barCta}>
              Continuar encomenda <Icon name="arrowRight" size={18} />
            </Link>
          </div>
        )}
      </div>
    </PublicLayout>
  )
}
