import type { ReactNode } from 'react'
import { formatLongDay } from '../../../../utils/date'
import type { StepIndex } from '../orderForm'
import type { StepProps } from './stepProps'
import styles from '../order.module.css'

interface ReviewProps extends StepProps {
  goTo: (step: StepIndex) => void
}

function Section({ title, step, goTo, children }: { title: string; step: StepIndex; goTo: (s: StepIndex) => void; children: ReactNode }) {
  return (
    <section className={styles.reviewSection}>
      <div className={styles.reviewHeader}>
        <h3>{title}</h3>
        <button type="button" className={styles.linkButton} onClick={() => goTo(step)}>
          Corrigir
        </button>
      </div>
      <dl className={styles.reviewList}>{children}</dl>
    </section>
  )
}

function Item({ label, value }: { label: string; value: ReactNode }) {
  if (value == null || value === '') return null
  return (
    <>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </>
  )
}

export function StepReview({ form, catalog, goTo }: ReviewProps) {
  const size = catalog.sizes.find((s) => s.id === form.sizeId)
  const names = (ids: number[], options: { id: number; name: string }[]) =>
    ids.map((id) => options.find((o) => o.id === id)?.name).filter(Boolean).join(', ')
  const photoCount = form.referencePhotos.length

  return (
    <section className={styles.card} aria-labelledby="step-title">
      <h2 id="step-title">Revise sua encomenda</h2>
      <p className={styles.hint}>Confira tudo antes de enviar. Você pode corrigir qualquer etapa.</p>
      <div className={styles.review}>
        <Section title="Seus dados" step={0} goTo={goTo}>
          <Item label="Nome" value={form.customerName.trim()} />
          <Item label="WhatsApp" value={form.customerWhatsapp.trim()} />
        </Section>
        <Section title="Retirada" step={1} goTo={goTo}>
          <Item label="Data" value={form.pickupDate && formatLongDay(form.pickupDate)} />
          <Item label="Horário" value={form.pickupTime} />
        </Section>
        <Section title="Seu bolo" step={2} goTo={goTo}>
          <Item label="Tamanho" value={size?.name} />
          <Item label="Massa" value={names(form.flavorIds, catalog.flavors)} />
          <Item label="Recheio" value={names(form.fillingIds, catalog.fillings)} />
          <Item label="Decoração" value={form.decoration.trim()} />
          <Item label="Nome/frase" value={form.cakeText.trim()} />
          <Item label="Observações" value={form.notes.trim()} />
          <Item label="Fotos" value={photoCount > 0 ? `${photoCount} ${photoCount === 1 ? 'foto' : 'fotos'}` : 'Nenhuma'} />
        </Section>
        <Section title="Topo de bolo" step={3} goTo={goTo}>
          {form.topperRequested ? (
            <>
              <Item label="Tema" value={form.topperTheme.trim()} />
              <Item label="Nome" value={form.topperName.trim()} />
              <Item label="Idade" value={form.topperAge.trim()} />
              <Item label="Foto" value={form.topperPhoto ? '1 foto' : 'Nenhuma'} />
            </>
          ) : (
            <Item label="Topo" value="Não solicitado" />
          )}
        </Section>
      </div>
      <p className={styles.hint}>
        Ao enviar, você recebe um código de acompanhamento. Sua solicitação será analisada e você receberá um orçamento.
        Seus dados são usados somente para esta encomenda.
      </p>
    </section>
  )
}
