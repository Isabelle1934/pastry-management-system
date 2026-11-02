import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { TextField } from '../../components/TextField'
import { AuthLayout } from '../../layouts/AuthLayout'
import { ApiError } from '../../services/api'
import { requestPasswordReset } from '../../services/auth'
import { validateEmail } from '../../utils/validation'
import styles from './forms.module.css'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState<string>()
  const [formError, setFormError] = useState<string>()
  const [sentMessage, setSentMessage] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const error = validateEmail(email)
    setEmailError(error)
    setFormError(undefined)
    if (error) return

    setSubmitting(true)
    try {
      const response = await requestPasswordReset(email.trim())
      setSentMessage(response.message)
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : 'Não foi possível enviar. Tente novamente.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Esqueci minha senha"
      subtitle="Informe o e-mail de acesso ao painel. Enviaremos um link para você criar uma nova senha."
    >
      {sentMessage ? (
        <Alert tone="success">{sentMessage}</Alert>
      ) : (
        <form className={styles.form} onSubmit={handleSubmit} noValidate>
          {formError && <Alert tone="error">{formError}</Alert>}
          <TextField
            label="E-mail"
            type="email"
            autoComplete="username"
            inputMode="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={emailError}
            maxLength={254}
            autoFocus
          />
          <Button type="submit" fullWidth loading={submitting}>
            Enviar link
          </Button>
        </form>
      )}
      <div className={styles.links}>
        <Link to="/admin/login">Voltar para o login</Link>
      </div>
    </AuthLayout>
  )
}
