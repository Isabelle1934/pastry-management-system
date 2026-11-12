import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router'
import { BrandMark } from '../components/BrandMark'
import { Icon } from '../components/Icon'
import { useBakeryInfo } from '../hooks/useBakeryInfo'
import motion from '../styles/motion.module.css'
import styles from './PublicLayout.module.css'

const YEAR = new Date().getFullYear()

interface PublicLayoutProps {
  children: ReactNode
  /** Página de largura total (a página inicial monta as próprias seções). */
  wide?: boolean
}

/** Moldura da área pública: cabeçalho com a marca, conteúdo e rodapé com contato. */
export function PublicLayout({ children, wide = false }: PublicLayoutProps) {
  const { data: bakery } = useBakeryInfo()
  const name = bakery?.name ?? 'Confeitaria'
  const navClass = ({ isActive }: { isActive: boolean }) => `${styles.navLink} ${isActive ? styles.navActive : ''}`

  return (
    <div className={styles.shell}>
      <a href="#conteudo" className={styles.skip}>Pular para o conteúdo</a>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link to="/" className={styles.brandLink} aria-label={`${name} — início`}>
            <BrandMark name={name} />
          </Link>
          <nav className={styles.nav} aria-label="Principal">
            <NavLink to="/cardapio" className={navClass}>
              <Icon name="bag" size={18} />
              <span className={styles.navText}>Cardápio</span>
            </NavLink>
            <NavLink to="/acompanhar" className={navClass}>
              <Icon name="search" size={18} />
              <span className={styles.navText}>Acompanhar pedido</span>
            </NavLink>
            <NavLink to="/encomendar" className={styles.navCta}>
              Encomendar
            </NavLink>
          </nav>
        </div>
      </header>

      <main id="conteudo" className={`${wide ? styles.mainWide : styles.main} ${motion.fadeIn}`} tabIndex={-1}>
        {children}
      </main>

      <footer className={styles.footer}>
        <div className={styles.footerInner}>
          <div className={styles.footerBrand}>
            <BrandMark name={name} tone="dark" />
            <p className={styles.footerTagline}>Bolos personalizados por encomenda.</p>
          </div>
          <div className={styles.footerCol}>
            <h2 className={styles.footerTitle}>Encomendas</h2>
            <Link to="/cardapio">Cardápio</Link>
            <Link to="/encomendar">Fazer encomenda</Link>
            <Link to="/acompanhar">Acompanhar pedido</Link>
          </div>
          {(bakery?.address || bakery?.whatsappLink || bakery?.instagram) && (
            <div className={styles.footerCol}>
              <h2 className={styles.footerTitle}>Contato</h2>
              {bakery.address && (
                <p className={styles.footerItem}>
                  <Icon name="mapPin" size={18} />
                  <span>{bakery.address}</span>
                </p>
              )}
              {bakery.whatsappLink && (
                <a href={bakery.whatsappLink} target="_blank" rel="noopener noreferrer" className={styles.footerItem}>
                  <Icon name="message" size={18} />
                  <span>WhatsApp {bakery.whatsappContact}</span>
                </a>
              )}
              {bakery.instagram && (
                <a href={`https://instagram.com/${bakery.instagram}`} target="_blank" rel="noopener noreferrer"
                  className={styles.footerItem}>
                  <Icon name="instagram" size={18} />
                  <span>@{bakery.instagram}</span>
                </a>
              )}
            </div>
          )}
        </div>
        <p className={styles.copyright}>© {YEAR} {name}</p>
      </footer>
    </div>
  )
}
