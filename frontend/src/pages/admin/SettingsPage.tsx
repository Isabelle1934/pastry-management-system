import { useState, type FormEvent, type ReactNode } from 'react'
import { Link } from 'react-router'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { TextAreaField } from '../../components/TextAreaField'
import { TextField } from '../../components/TextField'
import { useLoader } from '../../hooks/useLoader'
import { getSettings, saveSettings } from '../../services/settings'
import type { BakerySettings } from '../../types/settings'
import { validateEmail } from '../../utils/validation'
import styles from './panel.module.css'
import { describeSaveError } from './saveErrors'

type Field =
  | 'name'
  | 'aboutText'
  | 'whatsappContact'
  | 'instagram'
  | 'address'
  | 'pickupInstructions'
  | 'pixKey'
  | 'pixHolderName'
  | 'quoteValidityHours'
  | 'quoteExpiryWarningHours'
  | 'depositDeadlineHours'
  | 'ownerNotificationEmail'

type FormValues = Record<Field, string>

function toForm(settings: BakerySettings): FormValues {
  return {
    name: settings.name ?? '',
    aboutText: settings.aboutText ?? '',
    whatsappContact: settings.whatsappContact ?? '',
    instagram: settings.instagram ? `@${settings.instagram}` : '',
    address: settings.address ?? '',
    pickupInstructions: settings.pickupInstructions ?? '',
    pixKey: settings.pixKey ?? '',
    pixHolderName: settings.pixHolderName ?? '',
    quoteValidityHours: String(settings.quoteValidityHours),
    quoteExpiryWarningHours: String(settings.quoteExpiryWarningHours),
    depositDeadlineHours: settings.depositDeadlineHours == null ? '' : String(settings.depositDeadlineHours),
    ownerNotificationEmail: settings.ownerNotificationEmail ?? '',
  }
}

function Fieldset({ title, description, children }: { title: string; description?: string; children: ReactNode }) {
  return (
    <fieldset className={styles.card} style={{ margin: 0 }}>
      <legend className={styles.cardTitle} style={{ padding: '0 var(--space-1)' }}>
        {title}
      </legend>
      {description && <p className={styles.description}>{description}</p>}
      {children}
    </fieldset>
  )
}

function hoursHint(value: string): string | undefined {
  const hours = Number(value)
  if (!Number.isInteger(hours) || hours < 24) return undefined
  const days = hours / 24
  return Number.isInteger(days) ? `= ${days} ${days === 1 ? 'dia' : 'dias'}` : undefined
}

function SettingsForm({ initial, onSaved }: { initial: BakerySettings; onSaved: (s: BakerySettings) => void }) {
  const [values, setValues] = useState<FormValues>(() => toForm(initial))
  const [version, setVersion] = useState(initial.version)
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({})
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string }>()
  const [saving, setSaving] = useState(false)

  const set = (field: Field) => (event: { target: { value: string } }) =>
    setValues((current) => ({ ...current, [field]: event.target.value }))

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const validity = Number(values.quoteValidityHours)
    const warning = Number(values.quoteExpiryWarningHours)
    const nextErrors: Partial<Record<Field, string>> = {
      quoteValidityHours:
        Number.isInteger(validity) && validity >= 1 && validity <= 720 ? undefined : 'Use um número de 1 a 720 horas.',
      quoteExpiryWarningHours:
        !Number.isInteger(warning) || warning < 1
          ? 'Use um número de horas maior que zero.'
          : Number.isInteger(validity) && warning >= validity
            ? 'O aviso precisa acontecer antes do vencimento.'
            : undefined,
      depositDeadlineHours:
        values.depositDeadlineHours.trim() === '' ||
        (/^\d+$/.test(values.depositDeadlineHours.trim()) && Number(values.depositDeadlineHours) >= 1 &&
          Number(values.depositDeadlineHours) <= 720)
          ? undefined
          : 'Use um número de 1 a 720 horas, ou deixe em branco para não ter prazo.',
      ownerNotificationEmail: values.ownerNotificationEmail.trim()
        ? validateEmail(values.ownerNotificationEmail)
        : undefined,
    }
    setErrors(nextErrors)
    setNotice(undefined)
    if (Object.values(nextErrors).some(Boolean)) return

    setSaving(true)
    try {
      const saved = await saveSettings({
        name: values.name,
        aboutText: values.aboutText,
        whatsappContact: values.whatsappContact,
        instagram: values.instagram,
        address: values.address,
        pickupInstructions: values.pickupInstructions,
        pixKey: values.pixKey,
        pixHolderName: values.pixHolderName,
        quoteValidityHours: validity,
        quoteExpiryWarningHours: warning,
        depositDeadlineHours: values.depositDeadlineHours.trim() ? Number(values.depositDeadlineHours) : null,
        ownerNotificationEmail: values.ownerNotificationEmail,
        version,
      })
      setValues(toForm(saved))
      setVersion(saved.version)
      onSaved(saved)
      setNotice({ tone: 'success', text: 'Configurações salvas.' })
    } catch (error) {
      const failure = describeSaveError(error)
      setErrors(failure.fields as Partial<Record<Field, string>>)
      setNotice({
        tone: 'error',
        text: failure.stale
          ? 'As configurações foram alteradas em outra tela. Recarregue a página para ver a versão mais recente.'
          : (failure.message ?? 'Alguns campos precisam de correção.'),
      })
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <Fieldset title="Dados da confeitaria" description="Aparecem na página pública.">
        <TextField label="Nome da confeitaria" value={values.name} onChange={set('name')} error={errors.name} maxLength={120} />
        <TextAreaField
          label="Apresentação"
          value={values.aboutText}
          onChange={set('aboutText')}
          error={errors.aboutText}
          maxLength={2000}
          rows={4}
        />
        <div className={styles.row}>
          <TextField
            label="WhatsApp para contato"
            type="tel"
            inputMode="tel"
            placeholder="(11) 99999-8888"
            value={values.whatsappContact}
            onChange={set('whatsappContact')}
            error={errors.whatsappContact}
          />
          <TextField
            label="Instagram"
            placeholder="@minhaconfeitaria"
            value={values.instagram}
            onChange={set('instagram')}
            error={errors.instagram}
            maxLength={60}
          />
        </div>
        <TextField label="Endereço para retirada" value={values.address} onChange={set('address')} error={errors.address} maxLength={300} />
        <TextAreaField
          label="Instruções de retirada"
          value={values.pickupInstructions}
          onChange={set('pickupInstructions')}
          error={errors.pickupInstructions}
          maxLength={1000}
        />
      </Fieldset>

      <Fieldset title="Pagamento por Pix" description="Mostrado à cliente somente quando ela precisar pagar o sinal.">
        <div className={styles.row}>
          <TextField label="Chave Pix" value={values.pixKey} onChange={set('pixKey')} error={errors.pixKey} maxLength={77} />
          <TextField
            label="Nome do titular"
            value={values.pixHolderName}
            onChange={set('pixHolderName')}
            error={errors.pixHolderName}
            maxLength={120}
          />
        </div>
      </Fieldset>

      <Fieldset title="Orçamentos" description="Valem para os próximos orçamentos enviados.">
        <div className={styles.row}>
          <TextField
            label="Validade do orçamento (horas)"
            inputMode="numeric"
            value={values.quoteValidityHours}
            onChange={set('quoteValidityHours')}
            error={errors.quoteValidityHours}
            hint={hoursHint(values.quoteValidityHours) ?? 'Depois desse prazo sem resposta, o pedido é cancelado.'}
          />
          <TextField
            label="Avisar quantas horas antes de vencer"
            inputMode="numeric"
            value={values.quoteExpiryWarningHours}
            onChange={set('quoteExpiryWarningHours')}
            error={errors.quoteExpiryWarningHours}
            hint={hoursHint(values.quoteExpiryWarningHours)}
          />
        </div>
        <TextField
          label="Prazo para pagar o sinal depois do aceite (horas)"
          inputMode="numeric"
          value={values.depositDeadlineHours}
          onChange={set('depositDeadlineHours')}
          error={errors.depositDeadlineHours}
          hint={
            (hoursHint(values.depositDeadlineHours) ?? '') +
            ' Sem sinal nesse prazo, o pedido é cancelado automaticamente (exceto se houver comprovante em análise). ' +
            'Em branco = sem prazo.'
          }
        />
      </Fieldset>

      <Fieldset title="Avisos por e-mail" description="Para onde enviar avisos como orçamentos perto de vencer.">
        <TextField
          label="E-mail para avisos"
          type="email"
          inputMode="email"
          value={values.ownerNotificationEmail}
          onChange={set('ownerNotificationEmail')}
          error={errors.ownerNotificationEmail}
          maxLength={254}
        />
      </Fieldset>

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
      <div className={styles.actions}>
        <Button type="submit" loading={saving}>
          Salvar configurações
        </Button>
      </div>
    </form>
  )
}

export function SettingsPage() {
  const { data, loading, error, setData } = useLoader(getSettings)

  return (
    <div className={styles.page}>
      <h1>Configurações</h1>
      {loading && <p role="status">Carregando…</p>}
      {error && <Alert tone="error">{error}</Alert>}
      {data && <SettingsForm initial={data} onSaved={(saved) => setData(() => saved)} />}
      <p>
        <Link to="/admin/trocar-senha">Trocar minha senha</Link>
      </p>
    </div>
  )
}
