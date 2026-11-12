import { useCallback, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { Loading } from '../../components/Loading'
import { TextField } from '../../components/TextField'
import { useLoader } from '../../hooks/useLoader'
import { listCustomers } from '../../services/customers'
import { formatDate } from '../../utils/date'
import { formatBRL } from '../../utils/money'
import styles from './catalog/catalog.module.css'
import panel from './panel.module.css'

/** Clientes a partir dos pedidos (cada WhatsApp é uma cliente). Somente consulta. */
export function CustomersPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('busca') ?? ''
  const page = Number(params.get('pagina') ?? '0')
  const [search, setSearch] = useState(q)
  const load = useCallback(() => listCustomers(q, page), [q, page])
  const { data, loading, error } = useLoader(load)

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    setParams(search.trim() ? { busca: search.trim() } : {})
  }

  const goTo = (next: number) => {
    const merged = new URLSearchParams(params)
    merged.set('pagina', String(next))
    setParams(merged)
  }

  return (
    <div className={panel.page}>
      <h1>Clientes</h1>
      <p className={panel.intro}>
        Quem já fez encomendas, com o total de pedidos e o valor pago. Os dados vêm dos próprios pedidos — não há
        cadastro separado.
      </p>
      <form className={panel.toolbar} onSubmit={handleSearch} role="search">
        <TextField label="Buscar por nome ou WhatsApp" value={search} onChange={(e) => setSearch(e.target.value)} />
        <Button type="submit" variant="secondary">Buscar</Button>
      </form>

      {loading && <Loading />}
      {error && <Alert tone="error">{error}</Alert>}
      {data && data.content.length === 0 && (
        <p className={panel.empty}>{q ? 'Nenhuma cliente encontrada.' : 'Ainda não há encomendas.'}</p>
      )}
      {data && data.content.length > 0 && (
        <div className={`${panel.card} ${styles.tableWrap}`}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Cliente</th>
                <th>WhatsApp</th>
                <th className={styles.number}>Pedidos</th>
                <th className={styles.number}>Confirmados</th>
                <th className={styles.number}>Total pago</th>
                <th>Última data agendada</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((customer) => (
                <tr key={customer.whatsappDigits}>
                  <td>
                    <Link to={`/admin/pedidos?busca=${customer.whatsappDigits.slice(-8)}`}>{customer.name}</Link>
                  </td>
                  <td>
                    <a href={customer.whatsappLink} target="_blank" rel="noopener noreferrer">{customer.whatsapp}</a>
                  </td>
                  <td className={styles.number}>{customer.orders}</td>
                  <td className={styles.number}>{customer.confirmedOrders}</td>
                  <td className={styles.number}>{formatBRL(customer.totalPaid)}</td>
                  <td>{formatDate(customer.lastPickupDate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && data.totalPages > 1 && (
        <div className={panel.actions}>
          <Button variant="secondary" disabled={page === 0} onClick={() => goTo(page - 1)}>Anterior</Button>
          <span>Página {page + 1} de {data.totalPages}</span>
          <Button variant="secondary" disabled={page + 1 >= data.totalPages} onClick={() => goTo(page + 1)}>Próxima</Button>
        </div>
      )}
    </div>
  )
}
