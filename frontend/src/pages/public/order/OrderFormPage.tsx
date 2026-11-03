import { useCallback, useState } from 'react'
import { Link } from 'react-router'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { useLoader } from '../../../hooks/useLoader'
import { PublicLayout } from '../../../layouts/PublicLayout'
import { ApiError } from '../../../services/api'
import { getAvailability, getCatalog, submitOrder } from '../../../services/publicApi'
import { OrderSuccess } from './OrderSuccess'
import {
  FIELD_STEP,
  initialOrderForm,
  STEPS,
  toPayload,
  validateStep,
  type FieldErrors,
  type OrderFormState,
  type StepIndex,
} from './orderForm'
import styles from './order.module.css'
import { StepCake } from './steps/StepCake'
import { StepCustomer } from './steps/StepCustomer'
import { StepReview } from './steps/StepReview'
import { StepSchedule } from './steps/StepSchedule'
import { StepTopper } from './steps/StepTopper'
import { Loading } from '../../../components/Loading'

const LAST_STEP: StepIndex = 4

export function OrderFormPage() {
  const loadData = useCallback(async () => {
    const [catalog, availability] = await Promise.all([getCatalog(), getAvailability()])
    return { catalog, availability }
  }, [])
  const { data, loading, error, reload } = useLoader(loadData)

  const [step, setStep] = useState<StepIndex>(0)
  const [form, setForm] = useState<OrderFormState>(initialOrderForm)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [submitError, setSubmitError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)
  const [trackingCode, setTrackingCode] = useState<string>()

  const update = useCallback((patch: Partial<OrderFormState>) => {
    setForm((current) => ({ ...current, ...patch }))
    setErrors((current) => {
      const next = { ...current }
      for (const key of Object.keys(patch)) delete next[key as keyof OrderFormState]
      return next
    })
  }, [])

  function goTo(target: StepIndex) {
    setStep(target)
    setSubmitError(undefined)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function next() {
    const size = data?.catalog.sizes.find((s) => s.id === form.sizeId)
    const stepErrors = validateStep(step, form, size)
    setErrors(stepErrors)
    if (Object.keys(stepErrors).length === 0 && step < LAST_STEP) goTo((step + 1) as StepIndex)
  }

  async function submit() {
    setSubmitting(true)
    setSubmitError(undefined)
    try {
      const response = await submitOrder(toPayload(form), form.referencePhotos, form.topperPhoto)
      setTrackingCode(response.trackingCode)
      window.scrollTo({ top: 0 })
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        const fieldErrors = err.fieldErrors as FieldErrors
        setErrors(fieldErrors)
        const firstStep = Math.min(...Object.keys(fieldErrors).map((field) => FIELD_STEP[field] ?? LAST_STEP)) as StepIndex
        if (fieldErrors.pickupTime || fieldErrors.pickupDate) {
          // O horário pode ter sido ocupado ou bloqueado enquanto a cliente preenchia.
          setForm((current) => ({ ...current, pickupTime: '' }))
          await reload()
        }
        goTo(firstStep)
      } else {
        setSubmitError(err instanceof ApiError ? err.message : 'Não foi possível enviar. Tente novamente.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (trackingCode) {
    return (
      <PublicLayout>
        <OrderSuccess trackingCode={trackingCode} />
      </PublicLayout>
    )
  }

  return (
    <PublicLayout>
      <div className={styles.page}>
        <h1>Fazer encomenda</h1>
        <ol className={styles.stepper} aria-label="Etapas">
          {STEPS.map((label, index) => (
            <li
              key={label}
              className={`${styles.stepItem} ${index < step ? styles.stepDone : ''}`}
              aria-current={index === step ? 'step' : undefined}
            >
              <span className={styles.stepNumber}>{index + 1}</span>
              <span className={styles.stepLabel}>{label}</span>
            </li>
          ))}
        </ol>

        {loading && <Loading />}
        {error && (
          <Alert tone="error">
            {error} <Link to="/">Voltar ao início</Link>
          </Alert>
        )}
        {data && data.catalog.sizes.length === 0 && (
          <Alert tone="info">As encomendas ainda não estão abertas. Volte em breve!</Alert>
        )}

        {data && data.catalog.sizes.length > 0 && (
          <form
            onSubmit={(e) => {
              e.preventDefault()
              if (step === LAST_STEP) void submit()
              else next()
            }}
            noValidate
          >
            {(() => {
              const props = { form, errors, update, catalog: data.catalog, availability: data.availability }
              switch (step) {
                case 0:
                  return <StepCustomer {...props} />
                case 1:
                  return <StepSchedule {...props} />
                case 2:
                  return <StepCake {...props} />
                case 3:
                  return <StepTopper {...props} />
                default:
                  return <StepReview {...props} goTo={goTo} />
              }
            })()}

            {submitError && <Alert tone="error">{submitError}</Alert>}

            <div className={styles.nav} style={{ marginTop: 'var(--space-3)' }}>
              {step > 0 ? (
                <Button variant="secondary" onClick={() => goTo((step - 1) as StepIndex)} disabled={submitting}>
                  Voltar
                </Button>
              ) : (
                <span />
              )}
              {step === 1 && data.availability.days.length === 0 ? null : (
                <Button type="submit" loading={submitting}>
                  {step === LAST_STEP ? 'Enviar encomenda' : 'Continuar'}
                </Button>
              )}
            </div>
          </form>
        )}
      </div>
    </PublicLayout>
  )
}
