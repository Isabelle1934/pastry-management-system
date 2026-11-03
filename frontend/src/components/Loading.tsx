import styles from './Loading.module.css'

/** Indicador de carregamento acessível (o texto é lido pelos leitores de tela). */
export function Loading({ label = 'Carregando…' }: { label?: string }) {
  return (
    <p className={styles.loading} role="status">
      <span className={styles.spinner} aria-hidden="true" />
      {label}
    </p>
  )
}
