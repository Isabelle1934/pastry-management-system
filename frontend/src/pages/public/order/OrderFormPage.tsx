import { useCallback, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Alert } from '../../../components/Alert'
import { Button } from '../../../components/Button'
import { Icon } from '../../../components/Icon'
import { Loading } from '../../../components/Loading'
import { useLoader } from '../../../hooks/useLoader'
import { PublicLayout } from '../../../layouts/PublicLayout'
import { ApiError } from '../../../services/api'
import { getAvailability, getBakery, getCatalog, submitOrder } from '../../../services/publicApi'
import type { Availability, BakeryInfo, PublicCatalog } from '../../../types/public'
import { readCart, writeCart } from '../../../utils/cart'
import motion from '../../../styles/motion.module.css'
import { OrderSuccess } from './OrderSuccess'
import { OrderSummary } from './OrderSummary'
import {
  FIELD_STEP,
  initialOrderForm,
  STEP_LABELS,
  toPayload,
  validateStep,
  visibleSteps,
  type FieldErrors,
  type OrderFormState,
  type StepKey,
} from './orderForm'
import styles from './order.module.css'
import { StepCake } from './steps/StepCake'
import { StepCustomer } from './steps/StepCustomer'
import { StepItems } from './steps/StepItems'
import { StepReview } from './steps/StepReview'
import { StepSchedule } from './steps/StepSchedule'
import { StepTopper } from './steps/StepTopper'

interface WizardData {
  catalog: PublicCatalog
  availability: Availability
  bakery: BakeryInfo
}

const canOrderCake = (catalog: PublicCatalog) => catalog.customCakeEnabled && catalog.sizes.length > 0

/** Estado inicial: produtos escolhidos no cardápio (se ainda disponíveis) e bolo personalizado conforme o caso. */
function initialForm(catalog: PublicCatalog, wantsCake: boolean): OrderFormState {
  const items = readCart().filter((item) =>
    catalog.products.some((p) => p.id === item.productId && p.available && item.quantity >= p.minQuantity),
  )
  const hasProducts = catalog.products.some((p) => p.available)
  const customCake = canOrderCake(catalog) && (!hasProducts || wantsCake || items.length === 0)
  return { ...initialOrderForm, items, customCake }
}

function OrderWizard({ data, reload }: { data: WizardData; reload: () => Promise<void> }) {
  const { catalog, availability, bakery } = data
  const [searchParams] = useSearchParams()
  const hasProducts = catalog.products.some((p) => p.available)

  const [form, setForm] = useState<OrderFormState>(() => initialForm(catalog, searchParams.get('bolo') === '1'))
  const [step, setStep] = useState<StepKey>('customer')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [submitError, setSubmitError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)
  const [trackingCode, setTrackingCode] = useState<string>()

  const steps = visibleSteps(form, hasProducts)
  const index = Math.max(steps.indexOf(step), 0)
  const isLast = step === 'review'

  const update = useCallback((patch: Partial<OrderFormState>) => {
    setForm((current) => {
      const next = { ...current, ...patch }
      if (patch.items) writeCart(next.items)
      return next
    })
    setErrors((current) => {
      const next = { ...current }
      for (const key of Object.keys(patch)) delete next[key as keyof OrderFormState]
      return next
    })
  }, [])

  function goTo(target: StepKey) {
    setStep(target)
    setSubmitError(undefined)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function next() {
    const size = catalog.sizes.find((s) => s.id === form.sizeId)
    const stepErrors = validateStep(step, form, { size, products: catalog.products })
    setErrors(stepErrors)
    if (Object.keys(stepErrors).length === 0 && !isLast) goTo(steps[index + 1])
  }

  async function submit() {
    setSubmitting(true)
    setSubmitError(undefined)
    try {
      const photos = form.customCake ? form.referencePhotos : []
      const topperPhoto = form.customCake && form.topperRequested ? form.topperPhoto : null
      const response = await submitOrder(toPayload(form), photos, topperPhoto)
      writeCart([])
      setTrackingCode(response.trackingCode)
      window.scrollTo({ top: 0 })
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        const fieldErrors = err.fieldErrors as FieldErrors
        setErrors(fieldErrors)
        const target = steps.find((s) => Object.keys(fieldErrors).some((field) => FIELD_STEP[field] === s)) ?? 'review'
        if (fieldErrors.pickupTime || fieldErrors.pickupDate) {
          // O horário pode ter sido ocupado ou bloqueado enquanto a cliente preenchia.
          setForm((current) => ({ ...current, pickupTime: '' }))
          await reload()
        }
        goTo(target)
      } else {
        setSubmitError(err instanceof ApiError ? err.message : 'Não foi possível enviar. Tente novamente.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (trackingCode) return <OrderSuccess trackingCode={trackingCode} />

  const props = { form, errors, update, catalog, availability, bakery }
  return (
    <>
      <div className={styles.progress}>
        <div className={styles.progressTrack} aria-hidden="true">
          <div className={styles.progressBar} style={{ transform: `scaleX(${(index + 1) / steps.length})` }} />
        </div>
        <ol className={styles.stepper} aria-label="Etapas">
          {steps.map((key, i) => (
            <li
              key={key}
              className={`${styles.stepItem} ${i < index ? styles.stepDone : ''}`}
              aria-current={i === index ? 'step' : undefined}
            >
              <span className={styles.stepNumber}>{i + 1}</span>
              <span className={styles.stepLabel}>{STEP_LABELS[key]}</span>
            </li>
          ))}
        </ol>
        <p className={styles.progressText}>Etapa {index + 1} de {steps.length}</p>
      </div>

      <div className={styles.formLayout}>
        <form
          className={styles.form}
          onSubmit={(e) => {
            e.preventDefault()
            if (isLast) void submit()
            else next()
          }}
          noValidate
        >
          <div key={step} className={motion.slideIn}>
            {step === 'customer' && <StepCustomer {...props} />}
            {step === 'items' && <StepItems {...props} />}
            {step === 'cake' && <StepCake {...props} />}
            {step === 'topper' && <StepTopper {...props} />}
            {step === 'schedule' && <StepSchedule {...props} />}
            {step === 'review' && <StepReview {...props} goTo={goTo} steps={steps} />}
          </div>

          {submitError && <Alert tone="error">{submitError}</Alert>}

          <div className={styles.nav}>
            {index > 0 ? (
              <Button variant="secondary" onClick={() => goTo(steps[index - 1])} disabled={submitting}>
                <Icon name="arrowLeft" size={18} /> Voltar
              </Button>
            ) : (
              <span />
            )}
            {step === 'schedule' && availability.days.length === 0 ? null : (
              <Button type="submit" loading={submitting}>
                {isLast ? 'Enviar encomenda' : 'Continuar'} <Icon name={isLast ? 'check' : 'arrowRight'} size={18} />
              </Button>
            )}
          </div>
        </form>
        <OrderSummary form={form} catalog={catalog} />
      </div>
    </>
  )
}

export function OrderFormPage() {
  const loadData = useCallback(async () => {
    const [catalog, availability, bakery] = await Promise.all([getCatalog(), getAvailability(), getBakery()])
    return { catalog, availability, bakery }
  }, [])
  const { data, loading, error, reload } = useLoader(loadData)
  const open = data && (canOrderCake(data.catalog) || data.catalog.products.some((p) => p.available))

  return (
    <PublicLayout wide>
      <div className={`${styles.page} ${styles.formPage}`}>
        <header className={styles.pageHeader}>
          <p className={styles.eyebrow}>Encomenda</p>
          <h1>Fazer encomenda</h1>
          <p className={styles.hint}>Leva poucos minutos. Você recebe um orçamento personalizado antes de pagar qualquer valor.</p>
        </header>

        {loading && <Loading />}
        {error && (
          <Alert tone="error">
            {error} <Link to="/">Voltar ao início</Link>
          </Alert>
        )}
        {data && !open && <Alert tone="info">As encomendas ainda não estão abertas. Volte em breve!</Alert>}
        {data && open && <OrderWizard data={data} reload={reload} />}
      </div>
    </PublicLayout>
  )
}
