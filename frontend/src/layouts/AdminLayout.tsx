import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { BrandMark } from '../components/BrandMark'
import { Icon } from '../components/Icon'
import { useAuth } from '../hooks/useAuth'
import { useBakeryInfo } from '../hooks/useBakeryInfo'
import { unreadNotificationCount } from '../services/notifications'
import motion from '../styles/motion.module.css'
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
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const [menuPath, setMenuPath] = useState(location.pathname)
  const { data: bakery } = useBakeryInfo()

  // Fecha o menu do celular ao trocar de página.
  if (menuPath !== location.pathname) {
    setMenuPath(location.pathname)
    setMenuOpen(false)
  }

  async function handleLogout() {
    setLeaving(true)
    try {
      await logout()
    } finally {
      navigate('/admin/login', { replace: true })
    }
  }

  // Esc fecha o menu do celular.
  useEffect(() => {
    if (!menuOpen) return
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && setMenuOpen(false)
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [menuOpen])

  const linkClass = ({ isActive }: { isActive: boolean }) => `${styles.link} ${isActive ? styles.active : ''}`
  const badge = unread > 0 && <span className={styles.badge}>{unread > 99 ? '99+' : unread}</span>

  return (
    <div className={styles.shell}>
      <a href="#conteudo" className={styles.skip}>Pular para o conteúdo</a>
      <header className={styles.topbar}>
        <BrandMark name={bakery?.name ?? 'Confeitaria'} caption="Painel" />
        <button type="button" className={styles.menuButton} aria-expanded={menuOpen} aria-controls="menu-painel"
          aria-label={menuOpen ? 'Fechar menu' : unread > 0 ? `Menu (${unread} avisos não lidos)` : 'Menu'}
          onClick={() => setMenuOpen((open) => !open)}>
          <Icon name={menuOpen ? 'close' : 'menu'} size={20} />
          {menuOpen ? 'Fechar menu' : 'Menu'}
          {!menuOpen && unread > 0 && (
            <span className={styles.badge} aria-hidden="true">{unread > 99 ? '99+' : unread}</span>
          )}
        </button>
      </header>

      <div className={`${styles.overlay} ${menuOpen ? styles.overlayOpen : ''}`} aria-hidden="true"
        onClick={() => setMenuOpen(false)} />

      <aside className={`${styles.sidebar} ${menuOpen ? styles.open : ''}`}>
        <div className={styles.sidebarBrand}>
          <BrandMark name={bakery?.name ?? 'Confeitaria'} caption="Painel" tone="dark" />
        </div>
        <nav id="menu-painel" className={styles.nav} aria-label="Menu do painel">
          {user?.mustChangePassword ? (
            <NavLink to="/admin/trocar-senha" className={linkClass}>
              <Icon name="settings" /> Trocar senha
            </NavLink>
          ) : (
            <>
              <p className={styles.navGroup}>Rotina</p>
              <NavLink to="/admin" end className={linkClass}>
                <Icon name="home" /> Início
              </NavLink>
              <NavLink to="/admin/pedidos" className={linkClass}>
                <Icon name="orders" /> Pedidos
              </NavLink>
              <NavLink to="/admin/producao" className={linkClass}>
                <Icon name="production" /> Produção
              </NavLink>
              <NavLink to="/admin/agenda" className={linkClass}>
                <Icon name="calendar" /> Agenda
              </NavLink>
              <p className={styles.navGroup}>Negócio</p>
              <NavLink to="/admin/catalogo" className={linkClass}>
                <Icon name="cake" /> Catálogo
              </NavLink>
              <NavLink to="/admin/clientes" className={linkClass}>
                <Icon name="users" /> Clientes
              </NavLink>
              <NavLink to="/admin/financeiro" className={linkClass}>
                <Icon name="finance" /> Financeiro
              </NavLink>
              <NavLink to="/admin/relatorios" className={linkClass}>
                <Icon name="chart" /> Relatórios
              </NavLink>
              <p className={styles.navGroup}>Confeitaria</p>
              <NavLink to="/admin/configuracoes" className={linkClass}>
                <Icon name="settings" /> Configurações
              </NavLink>
              <NavLink to="/admin/avisos" className={linkClass}
                aria-label={unread > 0 ? `Avisos (${unread} não lidos)` : 'Avisos'}>
                <Icon name="bell" /> Avisos
                {badge}
              </NavLink>
            </>
          )}
        </nav>
        <div className={styles.sidebarFooter}>
          {user && (
            <p className={styles.user}>
              <span className={styles.avatar} aria-hidden="true">{user.name.trim().charAt(0).toUpperCase()}</span>
              <span className={styles.userName}>{user.name}</span>
            </p>
          )}
          <button type="button" className={styles.logout} onClick={handleLogout} disabled={leaving}>
            <Icon name="logout" size={18} /> {leaving ? 'Saindo…' : 'Sair'}
          </button>
        </div>
      </aside>

      <main id="conteudo" className={styles.content} tabIndex={-1}>
        <div key={location.pathname} className={`${styles.contentInner} ${motion.fadeUp}`}>
          <Outlet />
        </div>
      </main>
    </div>
  )
}
