import { useId } from 'react'
import { useBakeryInfo } from '../../../../hooks/useBakeryInfo'
import { formatLongDay, formatShortDay } from '../../../../utils/date'
import type { StepProps } from './stepProps'
import styles from '../order.module.css'

export function StepSchedule({ form, errors, update, availability }: StepProps) {
  const titleId = useId()
  const { data: bakery } = useBakeryInfo()
  const selectedDay = availability.days.find((day) => day.date === form.pickupDate)

  if (availability.days.length === 0) {
    return (
      <section className={styles.card} aria-labelledby={titleId}>
        <h2 id={titleId}>Data e horário de retirada</h2>
        <p>No momento não há horários disponíveis para encomenda.</p>
        {bakery?.whatsappLink && (
          <p>
            Fale com a gente pelo{' '}
            <a href={bakery.whatsappLink} target="_blank" rel="noopener noreferrer">
              WhatsApp
            </a>
            .
          </p>
        )}
      </section>
    )
  }

  return (
    <section className={styles.card} aria-labelledby={titleId}>
      <h2 id={titleId}>Data e horário de retirada</h2>
      <p className={styles.hint}>
        Mostramos somente datas com horário livre. A data só fica garantida depois da confirmação do sinal.
      </p>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Data</legend>
        <div className={styles.dateList}>
          {availability.days.map((day) => (
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
    </section>
  )
}
