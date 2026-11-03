import type { ReactNode } from 'react'
import { BrandMark } from '../components/BrandMark'
import { useBakeryInfo } from '../hooks/useBakeryInfo'
import motion from '../styles/motion.module.css'
import styles from './AuthLayout.module.css'

interface AuthLayoutProps {
  title: string
  subtitle?: string
  children: ReactNode
}

/** Moldura das telas de acesso ao painel (login, recuperação e redefinição de senha). */
export function AuthLayout({ title, subtitle, children }: AuthLayoutProps) {
  const { data: bakery } = useBakeryInfo()
  return (
    <main className={styles.page}>
      <div className={styles.aside} aria-hidden="true">
        <BrandMark name={bakery?.name ?? 'Confeitaria'} caption="Painel" tone="dark" />
        <p className={styles.quote}>Pedidos, agenda, produção e financeiro em um só lugar.</p>
      </div>
      <section className={`${styles.card} ${motion.fadeUp}`} aria-labelledby="auth-title">
        <p className={styles.eyebrow}>Painel administrativo</p>
        <h1 id="auth-title" className={styles.title}>
          {title}
        </h1>
        {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
        {children}
      </section>
    </main>
  )
}
