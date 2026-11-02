import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../hooks/useAuth'
import { unreadNotificationCount } from '../services/notifications'
import styles from './AdminLayout.module.css'

const POLL_MS = 60_000

/** Quantidade de avisos não lidos, atualizada a cada navegação e a cada minuto. */
function useUnreadCount(enabled: boolean): number {
  const location = useLocation()
  const [count, setCount] = useState(0)

  useEffect(() => {
    if (!enabled) return
    let active = true
    const refresh = () =>
      unreadNotificationCount().then(
        (result) => active && setCount(result.count),
        () => undefined,
      )
    void refresh()
    const timer = window.setInterval(() => void refresh(), POLL_MS)
    window.addEventListener('notifications:changed', refresh)
    return () => {
      active = false
      window.clearInterval(timer)
      window.removeEventListener('notifications:changed', refresh)
    }
  }, [enabled, location.pathname])

  return count
}

export function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [leaving, setLeaving] = useState(false)
  const unread = useUnreadCount(!!user && !user.mustChangePassword)

  async function handleLogout() {
    setLeaving(true)
    try {
      await logout()
    } finally {
      navigate('/admin/login', { replace: true })
    }
  }

  const linkClass = ({ isActive }: { isActive: boolean }) => `${styles.link} ${isActive ? styles.active : ''}`

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <div className={styles.brand}>
          <span className={styles.brandTitle}>Painel</span>
          {user && <span className={styles.user}>{user.name}</span>}
        </div>
        <nav className={styles.nav} aria-label="Menu do painel">
          {user?.mustChangePassword ? (
            <NavLink to="/admin/trocar-senha" className={linkClass}>
              Trocar senha
            </NavLink>
          ) : (
            <>
              <NavLink to="/admin" end className={linkClass}>
                Início
              </NavLink>
              <NavLink to="/admin/pedidos" className={linkClass}>
                Pedidos
              </NavLink>
              <NavLink to="/admin/agenda" className={linkClass}>
                Agenda
              </NavLink>
              <NavLink to="/admin/produtos" className={linkClass}>
                Produtos
              </NavLink>
              <NavLink to="/admin/configuracoes" className={linkClass}>
                Configurações
              </NavLink>
              <NavLink to="/admin/avisos" className={linkClass}
                aria-label={unread > 0 ? `Avisos (${unread} não lidos)` : 'Avisos'}>
                Avisos
                {unread > 0 && <span className={styles.badge}>{unread > 99 ? '99+' : unread}</span>}
              </NavLink>
            </>
          )}
          <button type="button" className={styles.logout} onClick={handleLogout} disabled={leaving}>
            {leaving ? 'Saindo…' : 'Sair'}
          </button>
        </nav>
      </header>
      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  )
}
