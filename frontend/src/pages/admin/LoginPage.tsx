import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { TextField } from '../../components/TextField'
import { useAuth } from '../../hooks/useAuth'
import { AuthLayout } from '../../layouts/AuthLayout'
import { ApiError } from '../../services/api'
import { validateEmail } from '../../utils/validation'
import styles from './forms.module.css'

interface LocationState {
  from?: string
}

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as LocationState | null)?.from

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({})
  const [formError, setFormError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const nextErrors = {
      email: validateEmail(email),
      password: password ? undefined : 'Informe a senha.',
    }
    setErrors(nextErrors)
    setFormError(undefined)
    if (nextErrors.email || nextErrors.password) return

    setSubmitting(true)
    try {
      const user = await login(email, password)
      const target = user.mustChangePassword ? '/admin/trocar-senha' : from?.startsWith('/admin') ? from : '/admin'
      navigate(target, { replace: true })
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Não foi possível entrar. Tente novamente.')
      setPassword('')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title="Entrar">
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}
        <TextField
          label="E-mail"
          type="email"
          autoComplete="username"
          inputMode="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={errors.email}
          maxLength={254}
          autoFocus
        />
        <TextField
          label="Senha"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
          maxLength={128}
        />
        <Button type="submit" fullWidth loading={submitting}>
          Entrar
        </Button>
      </form>
      <div className={styles.links}>
        <Link to="/admin/esqueci-senha">Esqueci minha senha</Link>
      </div>
    </AuthLayout>
  )
}
