import { useId } from 'react'
import { TextField } from '../../../../components/TextField'
import type { StepProps } from './stepProps'
import styles from '../order.module.css'

export function StepCustomer({ form, errors, update }: StepProps) {
  const titleId = useId()
  return (
    <section className={styles.card} aria-labelledby={titleId}>
      <h2 id={titleId}>Seus dados</h2>
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
      <TextField
        label="E-mail (opcional)"
        type="email"
        inputMode="email"
        autoComplete="email"
        hint="Se informar, enviamos um lembrete antes do prazo de pagamento do sinal."
        value={form.customerEmail}
        onChange={(e) => update({ customerEmail: e.target.value })}
        error={errors.customerEmail}
        maxLength={254}
      />
    </section>
  )
}
