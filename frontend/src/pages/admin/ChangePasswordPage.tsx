import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { TextField } from '../../components/TextField'
import { useAuth } from '../../hooks/useAuth'
import { ApiError } from '../../services/api'
import { changePassword } from '../../services/auth'
import {
  PASSWORD_MIN_LENGTH,
  validateConfirmation,
  validateNewPassword,
} from '../../utils/validation'
import styles from './forms.module.css'

interface FieldErrors {
  current?: string
  password?: string
  confirmation?: string
}

export function ChangePasswordPage() {
  const { user, refresh } = useAuth()
  const navigate = useNavigate()
  const provisional = user?.mustChangePassword ?? false

  const [current, setCurrent] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string>()
  const [success, setSuccess] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const nextErrors: FieldErrors = {
      current: current ? undefined : 'Informe a senha atual.',
      password: validateNewPassword(password),
      confirmation: validateConfirmation(password, confirmation),
    }
    setErrors(nextErrors)
    setFormError(undefined)
    setSuccess(false)
    if (nextErrors.current || nextErrors.password || nextErrors.confirmation) return

    setSubmitting(true)
    try {
      await changePassword(current, password)
      setCurrent('')
      setPassword('')
      setConfirmation('')
      const updated = await refresh()
      if (provisional && updated) {
        navigate('/admin', { replace: true })
        return
      }
      setSuccess(true)
    } catch (error) {
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
        const fields = error.fieldErrors
        setErrors({ current: fields.currentPassword, password: fields.newPassword })
      } else {
        setFormError(error instanceof ApiError ? error.message : 'Não foi possível salvar. Tente novamente.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className={styles.panel}>
      <h1>Trocar senha</h1>
      {provisional && (
        <Alert tone="info">Você está usando uma senha provisória. Defina uma senha pessoal para continuar.</Alert>
      )}
      {success && (
        <Alert tone="success">Senha alterada. Os outros aparelhos conectados foram desconectados.</Alert>
      )}
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}
        <TextField
          label="Senha atual"
          type="password"
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          error={errors.current}
          maxLength={128}
        />
        <TextField
          label="Nova senha"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
          hint={`Mínimo de ${PASSWORD_MIN_LENGTH} caracteres. Uma frase fácil de lembrar funciona bem.`}
          maxLength={128}
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
        <Button type="submit" loading={submitting}>
          Salvar nova senha
        </Button>
      </form>
    </section>
  )
}
