import type { ButtonHTMLAttributes } from 'react'
import styles from './Button.module.css'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary'
  loading?: boolean
  fullWidth?: boolean
}

export function Button({
  variant = 'primary',
  loading = false,
  fullWidth = false,
  disabled,
  children,
  className,
  type = 'button',
  ...props
}: ButtonProps) {
  const classes = [styles.button, styles[variant], fullWidth ? styles.fullWidth : '', className ?? '']
    .filter(Boolean)
    .join(' ')

  return (
    <button type={type} className={classes} disabled={disabled || loading} aria-busy={loading || undefined} {...props}>
      {loading ? (
        <>
          <span className={styles.spinner} aria-hidden="true" />
          Aguarde…
        </>
      ) : (
        children
      )}
    </button>
  )
}
