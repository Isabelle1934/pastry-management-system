import { useCallback, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { TextField } from '../../../components/TextField'
import { useLoader } from '../../../hooks/useLoader'
import { listOrders, orderCounts } from '../../../services/orders'
import type { CommercialStatus } from '../../../types/order'
import { formatShortDay } from '../../../utils/date'
import { formatBRL } from '../../../utils/money'
import { COMMERCIAL_STATUS, STATUS_FILTER_ORDER } from '../../../utils/orderStatus'
import panel from '../panel.module.css'
import styles from './orders.module.css'
import { StatusChip } from './StatusChip'
import { Loading } from '../../../components/Loading'

export function OrdersPage() {
  const [params, setParams] = useSearchParams()
  const status = (params.get('situacao') as CommercialStatus | null) ?? undefined
  const q = params.get('busca') ?? ''
  const page = Number(params.get('pagina') ?? '0')
  const [search, setSearch] = useState(q)

  const loadOrders = useCallback(() => listOrders({ status, q, page }), [status, q, page])
  const { data, loading, error } = useLoader(loadOrders)
  const { data: counts } = useLoader(orderCounts)

  function update(next: { situacao?: string; busca?: string; pagina?: number }) {
    const merged = new URLSearchParams(params)
    for (const [key, value] of Object.entries(next)) {
      if (value === undefined || value === '' || value === 0) merged.delete(key)
      else merged.set(key, String(value))
    }
    if (!('pagina' in next)) merged.delete('pagina')
    setParams(merged, { replace: true })
  }

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    update({ busca: search })
  }

  return (
    <div className={panel.page}>
      <h1>Pedidos</h1>

      <div className={styles.filters} role="group" aria-label="Filtrar por situação">
        <button type="button" className={styles.filter} aria-pressed={!status} onClick={() => update({ situacao: '' })}>
          Todos
        </button>
        {STATUS_FILTER_ORDER.map((value) => (
          <button
            key={value}
            type="button"
            className={styles.filter}
            aria-pressed={status === value}
            onClick={() => update({ situacao: value })}
          >
            {COMMERCIAL_STATUS[value].label}
            {counts && counts[value] > 0 && <span className={styles.count}>{counts[value]}</span>}
          </button>
        ))}
      </div>

      <form className={styles.searchRow} onSubmit={handleSearch} role="search">
        <TextField
          label="Buscar por nome, código ou WhatsApp"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          maxLength={60}
        />
        <Button type="submit" variant="secondary">
          Buscar
        </Button>
      </form>

      {loading && <Loading />}
      {error && <Alert tone="error">{error}</Alert>}
      {data && data.content.length === 0 && (
        <p className={panel.empty}>{q || status ? 'Nenhum pedido encontrado com esses filtros.' : 'Nenhum pedido ainda.'}</p>
      )}
      {data && data.content.length > 0 && (
        <ul className={panel.list}>
          {data.content.map((order) => (
            <li key={order.id}>
              <Link to={`/admin/pedidos/${order.id}`} className={styles.orderLink}>
                <div className={styles.row}>
                  <span className={styles.name}>{order.customerName}</span>
                  <StatusChip {...COMMERCIAL_STATUS[order.commercialStatus]} />
                </div>
                <div className={styles.row}>
                  <span>
                    {order.fulfillmentType === 'DELIVERY' ? 'Entrega' : 'Retirada'}: {formatShortDay(order.pickupDate)} às {order.pickupTime} · {order.summary}
                  </span>
                  {order.quoteTotal && <span>{formatBRL(order.quoteTotal)}</span>}
                </div>
                <span className={styles.code}>{order.trackingCode}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {data && data.totalPages > 1 && (
        <div className={styles.pager}>
          <Button variant="secondary" disabled={page === 0} onClick={() => update({ pagina: page - 1 })}>
            ← Anterior
          </Button>
          <span>
            Página {page + 1} de {data.totalPages}
          </span>
          <Button variant="secondary" disabled={page + 1 >= data.totalPages} onClick={() => update({ pagina: page + 1 })}>
            Próxima →
          </Button>
        </div>
      )}
    </div>
  )
}
