import { useCallback, useMemo, useState } from 'react'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { Icon } from '../../../components/Icon'
import { Loading } from '../../../components/Loading'
import { SelectField } from '../../../components/SelectField'
import { TextField } from '../../../components/TextField'
import { useLoader } from '../../../hooks/useLoader'
import { adminPhotoUrl, listCategories, listProducts, setProductStatus } from '../../../services/products'
import type { Product, ProductStatus } from '../../../types/product'
import { availabilityLabel, priceLabel } from '../../../utils/productRules'
import panel from '../panel.module.css'
import { describeSaveError } from '../saveErrors'
import styles from './catalog.module.css'
import { ProductForm } from './ProductForm'

const PRODUCT_STATUS: Record<ProductStatus, { label: string; hint: string }> = {
  ACTIVE: { label: 'Ativo', hint: 'aparece no catálogo e pode ser encomendado' },
  UNAVAILABLE: { label: 'Indisponível no momento', hint: 'aparece no catálogo, mas não pode ser escolhido' },
  INACTIVE: { label: 'Inativo', hint: 'fora do catálogo público (pedidos antigos não mudam)' },
}

async function loadProducts() {
  const [products, categories] = await Promise.all([listProducts(), listCategories()])
  return { products, categories }
}

export function ProductsSection() {
  const { data, loading, error, reload, setData } = useLoader(loadProducts)
  const [editing, setEditing] = useState<Product | 'new' | null>(null)
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string }>()
  const [busyId, setBusyId] = useState<number>()
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const [status, setStatus] = useState('')

  const replace = useCallback(
    (saved: Product) =>
      setData((current) => current && {
        ...current,
        products: current.products.some((p) => p.id === saved.id)
          ? current.products.map((p) => (p.id === saved.id ? saved : p))
          : [...current.products, saved],
      }),
    [setData],
  )

  const visible = useMemo(() => {
    const text = search.trim().toLocaleLowerCase('pt-BR')
    return (data?.products ?? []).filter(
      (p) =>
        (!text || p.name.toLocaleLowerCase('pt-BR').includes(text)) &&
        (!category || String(p.categoryId) === category) &&
        (!status || p.status === status),
    )
  }, [data, search, category, status])

  async function changeStatus(product: Product, next: ProductStatus) {
    setBusyId(product.id)
    setNotice(undefined)
    try {
      const saved = await setProductStatus(product, next)
      replace(saved)
      setNotice({ tone: 'success', text: `"${saved.name}": ${PRODUCT_STATUS[next].label.toLowerCase()} — ${PRODUCT_STATUS[next].hint}.` })
    } catch (err) {
      const failure = describeSaveError(err)
      setNotice({ tone: 'error', text: failure.message ?? 'Não foi possível alterar.' })
      if (failure.stale) await reload()
    } finally {
      setBusyId(undefined)
    }
  }

  const categories = data?.categories ?? []
  const hasActiveCategory = categories.some((c) => c.active)

  return (
    <section className={panel.section} aria-labelledby="products-title">
      <div className={panel.toolbar}>
        <h2 id="products-title">Produtos</h2>
        {editing === null && (
          <Button onClick={() => { setEditing('new'); setNotice(undefined) }} disabled={!hasActiveCategory}>
            <Icon name="plus" size={18} /> Adicionar produto
          </Button>
        )}
      </div>
      {data && !hasActiveCategory && (
        <Alert tone="info">Crie uma categoria na aba “Categorias” antes de cadastrar produtos.</Alert>
      )}

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
      {editing !== null && data && (
        <ProductForm
          key={editing === 'new' ? 'new' : editing.id}
          product={editing === 'new' ? undefined : editing}
          categories={categories}
          onSaved={(saved, close) => {
            replace(saved)
            if (close) {
              setEditing(null)
              setNotice({ tone: 'success', text: `Produto "${saved.name}" salvo.` })
            }
          }}
          onCancel={() => setEditing(null)}
          onStale={() => void reload()}
        />
      )}

      {loading && <Loading />}
      {error && <Alert tone="error">{error}</Alert>}
      {data && data.products.length > 0 && (
        <div className={styles.filters}>
          <TextField label="Buscar" placeholder="Nome do produto" value={search} onChange={(e) => setSearch(e.target.value)} />
          <SelectField label="Categoria" value={category} onChange={(e) => setCategory(e.target.value)}
            options={[{ value: '', label: 'Todas' }, ...categories.map((c) => ({ value: String(c.id), label: c.name }))]} />
          <SelectField label="Situação" value={status} onChange={(e) => setStatus(e.target.value)}
            options={[{ value: '', label: 'Todas' },
              ...(Object.keys(PRODUCT_STATUS) as ProductStatus[]).map((s) => ({ value: s, label: PRODUCT_STATUS[s].label }))]} />
        </div>
      )}
      {data && data.products.length === 0 && (
        <p className={panel.empty}>Nenhum produto cadastrado ainda. Use “Adicionar produto” para começar.</p>
      )}
      {data && data.products.length > 0 && visible.length === 0 && (
        <p className={panel.empty}>Nenhum produto com esses filtros.</p>
      )}
      {visible.length > 0 && (
        <ul className={panel.list}>
          {visible.map((product) => {
            const availability = availabilityLabel(product)
            return (
              <li key={product.id} className={`${panel.card} ${styles.productCard} ${product.status === 'INACTIVE' ? panel.inactive : ''}`}>
                <div className={styles.thumb}>
                  {product.photoIds[0] != null ? (
                    <img src={adminPhotoUrl(product.id, product.photoIds[0])} alt="" />
                  ) : (
                    <Icon name="image" size={24} />
                  )}
                </div>
                <div className={styles.productBody}>
                  <div className={panel.cardHeader}>
                    <div>
                      <h3 className={panel.cardTitle}>
                        {product.name}{' '}
                        {product.status !== 'ACTIVE' && <span className={panel.badge}>{PRODUCT_STATUS[product.status].label}</span>}
                        {product.seasonal && <span className={panel.badge}>Especial</span>}
                      </h3>
                      <p className={panel.description}>{product.categoryName}</p>
                    </div>
                    <span className={panel.price}>{priceLabel(product.price, product.unitLabel)}</span>
                  </div>
                  <ul className={panel.meta}>
                    {product.minQuantity > 1 && <li>Mínimo: {product.minQuantity}</li>}
                    {!product.orderable && <li>Não aceita encomendas pelo site</li>}
                    {availability && <li>{availability}</li>}
                    <li>{[product.pickupAllowed && 'Retirada', product.deliveryAllowed && 'Delivery'].filter(Boolean).join(' e ')}</li>
                    <li>{product.photoIds.length} {product.photoIds.length === 1 ? 'foto' : 'fotos'}</li>
                  </ul>
                  <div className={panel.actions}>
                    <Button variant="secondary" disabled={editing !== null}
                      onClick={() => { setEditing(product); setNotice(undefined) }}>
                      Editar
                    </Button>
                    <SelectField label="Situação" value={product.status} disabled={busyId === product.id}
                      onChange={(e) => void changeStatus(product, e.target.value as ProductStatus)}
                      options={(Object.keys(PRODUCT_STATUS) as ProductStatus[]).map((s) => ({ value: s, label: PRODUCT_STATUS[s].label }))} />
                  </div>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
