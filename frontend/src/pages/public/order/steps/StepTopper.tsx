import { TextField } from '../../../../components/TextField'
import { PhotoPicker } from '../PhotoPicker'
import type { StepProps } from './stepProps'
import styles from '../order.module.css'

export function StepTopper({ form, errors, update }: StepProps) {
  return (
    <section className={styles.card} aria-labelledby="step-title">
      <h2 id="step-title">Topo de bolo</h2>
      <p className={styles.hint}>O topo é opcional e tem valor à parte, informado no orçamento.</p>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Deseja topo de bolo?</legend>
        <div className={styles.choiceGrid}>
          <label className={styles.choice}>
            <input
              type="radio"
              name="topperRequested"
              checked={!form.topperRequested}
              onChange={() => update({ topperRequested: false, topperPhoto: null })}
            />
            <span className={styles.choiceTitle}>Não, obrigado(a)</span>
          </label>
          <label className={styles.choice}>
            <input
              type="radio"
              name="topperRequested"
              checked={form.topperRequested}
              onChange={() => update({ topperRequested: true })}
            />
            <span className={styles.choiceTitle}>Sim, quero topo</span>
          </label>
        </div>
      </fieldset>

      {form.topperRequested && (
        <>
          <TextField
            label="Tema"
            placeholder="Ex.: jardim encantado, futebol, princesas"
            value={form.topperTheme}
            onChange={(e) => update({ topperTheme: e.target.value })}
            error={errors.topperTheme}
            maxLength={100}
          />
          <TextField
            label="Nome no topo (opcional)"
            value={form.topperName}
            onChange={(e) => update({ topperName: e.target.value })}
            error={errors.topperName}
            maxLength={60}
          />
          <TextField
            label="Idade (opcional)"
            inputMode="numeric"
            value={form.topperAge}
            onChange={(e) => update({ topperAge: e.target.value })}
            error={errors.topperAge}
            maxLength={3}
          />
          <PhotoPicker
            label="Foto de referência do topo (opcional)"
            hint="1 foto em JPG, PNG ou WEBP, com até 5 MB."
            photos={form.topperPhoto ? [form.topperPhoto] : []}
            max={1}
            error={errors.topperPhoto}
            onChange={(photos) => update({ topperPhoto: photos[0] ?? null })}
          />
        </>
      )}
    </section>
  )
}
