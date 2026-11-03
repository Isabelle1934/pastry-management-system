import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { useLoader } from '../../hooks/useLoader'
import {
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type AdminNotification,
} from '../../services/notifications'
import { formatDateTime } from '../../utils/orderStatus'
import styles from './orders/orders.module.css'
import panel from './panel.module.css'
import { Loading } from '../../components/Loading'

export function NotificationsPage() {
  const { data, loading, error, reload } = useLoader(listNotifications)
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)

  async function open(notification: AdminNotification) {
    if (!notification.read) await markNotificationRead(notification.id)
    if (notification.orderId) navigate(`/admin/pedidos/${notification.orderId}`)
    else await reload()
  }

  async function readAll() {
    setBusy(true)
    try {
      await markAllNotificationsRead()
      await reload()
      window.dispatchEvent(new Event('notifications:changed'))
    } finally {
      setBusy(false)
    }
  }

  const unread = data?.filter((n) => !n.read).length ?? 0

  return (
    <div className={panel.page}>
      <div className={panel.toolbar}>
        <h1>Avisos</h1>
        {unread > 0 && (
          <Button variant="secondary" onClick={() => void readAll()} loading={busy}>
            Marcar todos como lidos
          </Button>
        )}
      </div>
      <p className={styles.muted}>
        Novas encomendas, respostas a orçamentos, comprovantes e prazos. Os avisos importantes também chegam por e-mail.
      </p>
      {loading && <Loading />}
      {error && <Alert tone="error">{error}</Alert>}
      {data && data.length === 0 && <p className={panel.empty}>Nenhum aviso por enquanto.</p>}
      {data && data.length > 0 && (
        <ul className={panel.list}>
          {data.map((notification) => (
            <li key={notification.id}>
              <button
                type="button"
                className={styles.orderLink}
                style={{ width: '100%', textAlign: 'left', cursor: 'pointer', font: 'inherit',
                  fontWeight: notification.read ? 400 : 600 }}
                onClick={() => void open(notification)}
              >
                <span className={styles.row}>
                  <span>{notification.title}</span>
                  {!notification.read && <span className={`${styles.chip} ${styles.info}`}>Novo</span>}
                </span>
                <span>{notification.message}</span>
                <span className={styles.muted}>{formatDateTime(notification.createdAt)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
