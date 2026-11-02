import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../hooks/useAuth'
import styles from './AdminLayout.module.css'

export function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [leaving, setLeaving] = useState(false)

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
