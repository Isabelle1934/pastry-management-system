import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { useBakeryInfo } from '../hooks/useBakeryInfo'
import styles from './PublicLayout.module.css'

/** Moldura da área pública. Visual neutro provisório até a identidade visual oficial (pendência P1). */
export function PublicLayout({ children }: { children: ReactNode }) {
  const { data: bakery } = useBakeryInfo()
  const name = bakery?.name ?? 'Confeitaria'

  return (
    <div className={styles.shell}>
      <a href="#conteudo" className={styles.skip}>Pular para o conteúdo</a>
      <header className={styles.header}>
        <Link to="/" className={styles.brand}>
          {name}
        </Link>
      </header>
      <main id="conteudo" className={styles.main} tabIndex={-1}>{children}</main>
      <footer className={styles.footer}>
        <p className={styles.footerName}>{name}</p>
        {bakery?.address && <p>{bakery.address}</p>}
        <p className={styles.footerLinks}>
          {bakery?.whatsappLink && (
            <a href={bakery.whatsappLink} target="_blank" rel="noopener noreferrer">
              WhatsApp {bakery.whatsappContact}
            </a>
          )}
          {bakery?.instagram && (
            <a href={`https://instagram.com/${bakery.instagram}`} target="_blank" rel="noopener noreferrer">
              @{bakery.instagram}
            </a>
          )}
        </p>
      </footer>
    </div>
  )
}
