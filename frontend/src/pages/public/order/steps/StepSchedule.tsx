import { useEffect, useId, useMemo } from 'react'
import { TextAreaField } from '../../../../components/TextAreaField'
import { TextField } from '../../../../components/TextField'
import { formatLongDay, formatShortDay } from '../../../../utils/date'
import { acceptsDate, allowedFulfillment, FULFILLMENT_LABELS } from '../../../../utils/productRules'
import type { StepProps } from './stepProps'
import styles from '../order.module.css'

/** Modalidade (retirada ou delivery), data e horário — só datas com vaga e aceitas por todos os produtos. */
export function StepSchedule({ form, errors, update, availability, catalog, bakery }: StepProps) {
  const titleId = useId()
  const chosen = useMemo(
    () => catalog.products.filter((p) => form.items.some((i) => i.productId === p.id)),
    [catalog.products, form.items],
  )
  const modes = allowedFulfillment(bakery, form.customCake, chosen)
  const days = availability.days.filter((day) => chosen.every((p) => acceptsDate(p, day.date)))
  const selectedDay = days.find((day) => day.date === form.pickupDate)
  const delivery = form.fulfillmentType === 'DELIVERY'

  // Se a forma escolhida deixou de ser possível (ex.: produto sem delivery), usa a primeira disponível.
  useEffect(() => {
    if (modes.length > 0 && !modes.includes(form.fulfillmentType)) update({ fulfillmentType: modes[0] })
  }, [modes, form.fulfillmentType, update])

  return (
    <section className={styles.card} aria-labelledby={titleId}>
      <h2 id={titleId}>{bakery.deliveryEnabled ? 'Retirada ou entrega' : 'Data e horário de retirada'}</h2>

      {bakery.deliveryEnabled && (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Como você prefere receber?</legend>
          {modes.length === 0 ? (
            <p className={styles.error} role="alert">
              Os itens escolhidos não têm uma forma de atendimento em comum. Faça pedidos separados ou fale com a
              gente pelo WhatsApp.
            </p>
          ) : (
            <div className={styles.choiceGrid}>
              {(['PICKUP', 'DELIVERY'] as const).map((mode) => (
                <label key={mode} className={styles.choice}>
                  <input
                    type="radio"
                    name="fulfillmentType"
                    checked={form.fulfillmentType === mode}
                    disabled={!modes.includes(mode)}
                    onChange={() => update({ fulfillmentType: mode })}
                  />
                  <span className={styles.choiceTitle}>{FULFILLMENT_LABELS[mode]}</span>
                  {!modes.includes(mode) && (
                    <span className={styles.choiceMeta}>Indisponível para este pedido</span>
                  )}
                </label>
              ))}
            </div>
          )}
          {delivery && bakery.deliveryInfo && <p className={styles.hint}>{bakery.deliveryInfo}</p>}
          {errors.fulfillmentType && (
            <p className={styles.error} role="alert">
              {errors.fulfillmentType}
            </p>
          )}
        </fieldset>
      )}

      {delivery && (
        <>
          <TextField
            label="Endereço de entrega"
            autoComplete="street-address"
            placeholder="Rua, número e complemento"
            value={form.deliveryAddress}
            onChange={(e) => update({ deliveryAddress: e.target.value })}
            error={errors.deliveryAddress}
            maxLength={300}
          />
          <TextField
            label="Bairro (opcional)"
            value={form.deliveryDistrict}
            onChange={(e) => update({ deliveryDistrict: e.target.value })}
            error={errors.deliveryDistrict}
            maxLength={100}
          />
          <TextField
            label="Ponto de referência (opcional)"
            value={form.deliveryReference}
            onChange={(e) => update({ deliveryReference: e.target.value })}
            error={errors.deliveryReference}
            maxLength={200}
          />
        </>
      )}

      {days.length === 0 ? (
        <p>
          No momento não há datas disponíveis{chosen.length > 0 ? ' para os itens escolhidos' : ''}.
          {bakery.whatsappLink && (
            <>
              {' '}Fale com a gente pelo{' '}
              <a href={bakery.whatsappLink} target="_blank" rel="noopener noreferrer">
                WhatsApp
              </a>
              .
            </>
          )}
        </p>
      ) : (
        <>
          <p className={styles.hint}>
            Mostramos somente datas com horário livre. A data só fica garantida depois da confirmação do sinal de 50%.
          </p>
          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>Data</legend>
            <div className={styles.dateList}>
              {days.map((day) => (
                <label key={day.date} className={styles.choice}>
                  <input
                    type="radio"
                    name="pickupDate"
                    value={day.date}
                    checked={form.pickupDate === day.date}
                    onChange={() => update({ pickupDate: day.date, pickupTime: day.times.length === 1 ? day.times[0] : '' })}
                  />
                  <span className={styles.choiceTitle}>{formatShortDay(day.date)}</span>
                </label>
              ))}
            </div>
            {errors.pickupDate && (
              <p className={styles.error} role="alert">
                {errors.pickupDate}
              </p>
            )}
          </fieldset>

          {selectedDay && (
            <fieldset className={styles.fieldset}>
              <legend className={styles.legend}>Horário em {formatLongDay(selectedDay.date)}</legend>
              <div className={styles.timeList}>
                {selectedDay.times.map((time) => (
                  <label key={time} className={styles.choice}>
                    <input
                      type="radio"
                      name="pickupTime"
                      value={time}
                      checked={form.pickupTime === time}
                      onChange={() => update({ pickupTime: time })}
                    />
                    <span className={styles.choiceTitle}>{time}</span>
                  </label>
                ))}
              </div>
            </fieldset>
          )}
          {errors.pickupTime && (
            <p className={styles.error} role="alert">
              {errors.pickupTime}
            </p>
          )}
        </>
      )}

      <TextAreaField
        label="Observações (opcional)"
        hint="Alergias, restrições ou qualquer detalhe importante."
        value={form.notes}
        onChange={(e) => update({ notes: e.target.value })}
        error={errors.notes}
        maxLength={1000}
      />
    </section>
  )
}
