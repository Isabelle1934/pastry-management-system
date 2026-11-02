import { TextField } from '../../../../components/TextField'
import type { StepProps } from './stepProps'
import styles from '../order.module.css'

export function StepCustomer({ form, errors, update }: StepProps) {
  return (
    <section className={styles.card} aria-labelledby="step-title">
      <h2 id="step-title">Seus dados</h2>
      <p className={styles.hint}>Usaremos seu WhatsApp para falar sobre a encomenda.</p>
      <TextField
        label="Nome completo"
        autoComplete="name"
        value={form.customerName}
        onChange={(e) => update({ customerName: e.target.value })}
        error={errors.customerName}
        maxLength={120}
      />
      <TextField
        label="WhatsApp (com DDD)"
        type="tel"
        inputMode="tel"
        autoComplete="tel-national"
        placeholder="(11) 99999-8888"
        value={form.customerWhatsapp}
        onChange={(e) => update({ customerWhatsapp: e.target.value })}
        error={errors.customerWhatsapp}
        maxLength={30}
      />
    </section>
  )
}
