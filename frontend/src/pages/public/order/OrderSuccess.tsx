import { useState } from 'react'
import { Link } from 'react-router'
import { Button } from '../../../components/Button'
import { Icon } from '../../../components/Icon'
import motion from '../../../styles/motion.module.css'
import styles from './order.module.css'

export function OrderSuccess({ trackingCode, punctualityNotice }: { trackingCode: string; punctualityNotice?: string }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(trackingCode)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  return (
    <section className={`${styles.card} ${motion.fadeUp}`} aria-labelledby="success-title">
      <span className={styles.successMark}><Icon name="check" size={30} /></span>
      <h2 id="success-title">Encomenda enviada!</h2>
      <p>Recebemos sua solicitação. Este é o seu código de acompanhamento:</p>
      <output className={styles.code} aria-label="Código de acompanhamento">
        {trackingCode}
      </output>
      <Button variant="secondary" onClick={() => void copy()}>
        <Icon name={copied ? 'check' : 'copy'} size={18} /> {copied ? 'Código copiado!' : 'Copiar código'}
      </Button>
      <p>
        <strong>Guarde este código</strong> (tire um print ou copie). Ele será usado, junto com o seu WhatsApp, para
        acompanhar o pedido e ver o orçamento.
      </p>
      <p className={styles.hint}>
        Próximos passos: vamos analisar sua solicitação e preparar um orçamento personalizado. A data escolhida fica
        garantida depois da confirmação do sinal de 50%. Esta é uma solicitação de orçamento: o pedido só fica
        confirmado depois do sinal.
      </p>
      {punctualityNotice && (
        <div className={styles.notice} role="note" aria-label="Aviso de pontualidade">
          <strong>Pontualidade</strong>
          <p style={{ margin: 0 }}>{punctualityNotice}</p>
        </div>
      )}
      <p>
        <Link to="/acompanhar">Acompanhar pedido</Link> · <Link to="/">Voltar ao início</Link>
      </p>
    </section>
  )
}
