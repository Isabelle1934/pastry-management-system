import { useId, type ReactNode } from 'react'
import { formatLongDay } from '../../../../utils/date'
import { formatBRL } from '../../../../utils/money'
import { DELIVERY_ZONE_LABELS, deliveryZoneFee, FULFILLMENT_LABELS, priceLabel } from '../../../../utils/productRules'
import type { StepKey } from '../orderForm'
import type { StepProps } from './stepProps'
import styles from '../order.module.css'

interface ReviewProps extends StepProps {
  goTo: (step: StepKey) => void
  steps: StepKey[]
}

function Section({ title, step, goTo, children }: { title: string; step: StepKey; goTo: (s: StepKey) => void; children: ReactNode }) {
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

export function StepReview({ form, catalog, bakery, goTo, steps }: ReviewProps) {
  const titleId = useId()
  const size = catalog.sizes.find((s) => s.id === form.sizeId)
  const names = (ids: number[], options: { id: number; name: string }[]) =>
    ids.map((id) => options.find((o) => o.id === id)?.name).filter(Boolean).join(', ')
  const photoCount = form.referencePhotos.length
  const delivery = form.fulfillmentType === 'DELIVERY'

  return (
    <section className={styles.card} aria-labelledby={titleId}>
      <h2 id={titleId}>Revise sua encomenda</h2>
      <p className={styles.hint}>Confira tudo antes de enviar. Você pode corrigir qualquer etapa.</p>
      <div className={styles.review}>
        <Section title="Seus dados" step="customer" goTo={goTo}>
          <Item label="Nome" value={form.customerName.trim()} />
          <Item label="WhatsApp" value={form.customerWhatsapp.trim()} />
          <Item label="E-mail" value={form.customerEmail.trim()} />
        </Section>
        {steps.includes('items') && form.items.length > 0 && (
          <Section title="Produtos" step="items" goTo={goTo}>
            {form.items.map((item) => {
              const product = catalog.products.find((p) => p.id === item.productId)
              if (!product) return null
              return (
                <Item
                  key={item.productId}
                  label={`${item.quantity} × ${product.name}`}
                  value={priceLabel(product.price, product.unitLabel)}
                />
              )
            })}
          </Section>
        )}
        {form.customCake && (
          <Section title="Bolo personalizado" step="cake" goTo={goTo}>
            <Item label="Tipo" value={catalog.cakeCategories.find((c) => c.id === (size?.categoryId ?? form.cakeCategoryId))?.name} />
            <Item label="Tamanho" value={size?.name ?? (form.cakeCategoryId ? 'Consulte valores (valor no orçamento)' : undefined)} />
            <Item label="Massa" value={names(form.flavorIds, catalog.flavors)} />
            <Item label="Recheio" value={names(form.fillingIds, catalog.fillings)} />
            <Item label="Decoração" value={form.decoration.trim()} />
            <Item label="Nome/frase" value={form.cakeText.trim()} />
            <Item label="Fotos" value={photoCount > 0 ? `${photoCount} ${photoCount === 1 ? 'foto' : 'fotos'}` : 'Nenhuma'} />
          </Section>
        )}
        {form.customCake && (
          <Section title="Topo de bolo" step="topper" goTo={goTo}>
            {form.topperRequested ? (
              <>
                <Item label="Tema" value={form.topperTheme.trim()} />
                <Item label="Nome" value={form.topperName.trim()} />
                <Item label="Idade" value={form.topperAge.trim()} />
                <Item label="Foto" value={form.topperPhoto ? '1 foto' : 'Nenhuma'} />
                <Item label="Valor" value="Cobrado à parte, no orçamento" />
              </>
            ) : (
              <Item label="Topo" value="Não solicitado" />
            )}
          </Section>
        )}
        <Section title={delivery ? 'Entrega' : 'Retirada'} step="schedule" goTo={goTo}>
          <Item label="Forma" value={FULFILLMENT_LABELS[form.fulfillmentType]} />
          <Item label="Data" value={form.pickupDate && formatLongDay(form.pickupDate)} />
          <Item label="Horário" value={form.pickupTime} />
          {delivery && <Item label="Endereço" value={[form.deliveryAddress.trim(), form.deliveryDistrict.trim()].filter(Boolean).join(' – ')} />}
          {delivery && <Item label="Referência" value={form.deliveryReference.trim()} />}
          {delivery && <Item label="Modalidade" value={DELIVERY_ZONE_LABELS[form.deliveryZone]} />}
          {delivery && (
            <Item label="Taxa de entrega" value={(() => {
              const fee = deliveryZoneFee(bakery, form.deliveryZone, form.customCake)
              return fee ? `${formatBRL(fee)} (entra separada no orçamento)`
                : form.deliveryZone === 'OUTSIDE_CITY' ? 'Combinada conforme a localização, no orçamento'
                  : 'Informada no orçamento'
            })()} />
          )}
          <Item label="Observações" value={form.notes.trim()} />
        </Section>
      </div>
      {bakery.punctualityNotice && (
        <div className={styles.notice} role="note" aria-label="Aviso de pontualidade">
          <strong>Pontualidade</strong>
          <p style={{ margin: 0 }}>{bakery.punctualityNotice}</p>
        </div>
      )}
      <p className={styles.hint}>
        Ao enviar, você recebe um código de acompanhamento. Sua solicitação será analisada e você receberá um orçamento
        com o valor final. Para confirmar, é pedido um sinal de 50%; o restante é pago na retirada ou na entrega.
        Seus dados são usados somente para esta encomenda.
      </p>
    </section>
  )
}
