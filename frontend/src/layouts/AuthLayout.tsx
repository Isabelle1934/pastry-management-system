import type { ReactNode } from 'react'
import styles from './AuthLayout.module.css'

interface AuthLayoutProps {
  title: string
  subtitle?: string
  children: ReactNode
}

/** Moldura das telas de acesso ao painel (login, recuperação e redefinição de senha). */
export function AuthLayout({ title, subtitle, children }: AuthLayoutProps) {
  return (
    <main className={styles.page}>
      <section className={styles.card} aria-labelledby="auth-title">
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
