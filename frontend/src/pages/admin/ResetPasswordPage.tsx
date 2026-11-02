import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { TextField } from '../../components/TextField'
import { AuthLayout } from '../../layouts/AuthLayout'
import { ApiError } from '../../services/api'
import { resetPassword } from '../../services/auth'
import {
  PASSWORD_MIN_LENGTH,
  validateConfirmation,
  validateNewPassword,
} from '../../utils/validation'
import styles from './forms.module.css'

/**
 * O token chega no fragmento da URL (#token=...), que não é enviado ao servidor.
 * Ele é lido uma única vez e removido da barra de endereços (e do histórico).
 */
function takeTokenFromUrl(): string | null {
  const token = new URLSearchParams(window.location.hash.slice(1)).get('token')
  if (window.location.hash) {
    window.history.replaceState(null, '', window.location.pathname)
  }
  return token
}

export function ResetPasswordPage() {
  const [token] = useState(takeTokenFromUrl)
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [errors, setErrors] = useState<{ password?: string; confirmation?: string }>({})
  const [formError, setFormError] = useState<string>()
  const [invalidLink, setInvalidLink] = useState(!token)
  const [done, setDone] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const nextErrors = {
      password: validateNewPassword(password),
      confirmation: validateConfirmation(password, confirmation),
    }
    setErrors(nextErrors)
    setFormError(undefined)
    if (nextErrors.password || nextErrors.confirmation || !token) return

    setSubmitting(true)
    try {
      await resetPassword(token, password)
      setDone(true)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'INVALID_RESET_TOKEN') {
        setInvalidLink(true)
      } else if (error instanceof ApiError && error.fieldErrors.newPassword) {
        setErrors({ password: error.fieldErrors.newPassword })
      } else {
        setFormError(error instanceof ApiError ? error.message : 'Não foi possível salvar. Tente novamente.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (done) {
    return (
      <AuthLayout title="Senha alterada">
        <Alert tone="success">Sua nova senha foi salva. Por segurança, você foi desconectada de todos os aparelhos.</Alert>
        <div className={styles.links}>
          <Link to="/admin/login">Entrar com a nova senha</Link>
        </div>
      </AuthLayout>
    )
  }

  if (invalidLink) {
    return (
      <AuthLayout title="Link inválido">
        <Alert tone="error">Este link de redefinição é inválido ou expirou. Solicite um novo.</Alert>
        <div className={styles.links}>
          <Link to="/admin/esqueci-senha">Solicitar novo link</Link>
          <Link to="/admin/login">Voltar para o login</Link>
        </div>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Criar nova senha">
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}
        <TextField
          label="Nova senha"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
          hint={`Mínimo de ${PASSWORD_MIN_LENGTH} caracteres. Uma frase fácil de lembrar funciona bem.`}
          maxLength={128}
          autoFocus
        />
        <TextField
          label="Repita a nova senha"
          type="password"
          autoComplete="new-password"
          value={confirmation}
          onChange={(e) => setConfirmation(e.target.value)}
          error={errors.confirmation}
          maxLength={128}
        />
        <Button type="submit" fullWidth loading={submitting}>
          Salvar nova senha
        </Button>
      </form>
    </AuthLayout>
  )
}
