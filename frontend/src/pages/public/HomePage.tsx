import { Link } from 'react-router'
import { Alert } from '../../components/Alert'
import { useBakeryInfo } from '../../hooks/useBakeryInfo'
import { PublicLayout } from '../../layouts/PublicLayout'
import styles from './public.module.css'

/**
 * Página inicial. Fotos dos produtos, logo e identidade visual entram quando forem fornecidos (pendência P1);
 * até lá a página mostra apenas as informações cadastradas em Configurações.
 */
export function HomePage() {
  const { data: bakery, error } = useBakeryInfo()

  return (
    <PublicLayout>
      <section className={styles.hero}>
        <h1 className={styles.heroTitle}>{bakery?.name ?? 'Encomende seu bolo'}</h1>
        {bakery?.aboutText && <p className={styles.about}>{bakery.aboutText}</p>}
        <Link to="/encomendar" className={styles.cta}>
          Fazer encomenda
        </Link>
        <Link to="/acompanhar">Já fez uma encomenda? Acompanhe aqui</Link>
      </section>

      {error && <Alert tone="error">{error}</Alert>}

      <section className={styles.infoGrid} aria-label="Informações">
        <article className={styles.infoCard}>
          <h2>Como funciona</h2>
          <ol className={styles.steps}>
            <li>Escolha a data de retirada e personalize seu bolo.</li>
            <li>Você recebe um orçamento personalizado.</li>
            <li>Com o orçamento aceito, o pedido é confirmado com 50% de sinal.</li>
            <li>O restante é pago na retirada.</li>
          </ol>
        </article>
        {(bakery?.address || bakery?.pickupInstructions) && (
          <article className={styles.infoCard}>
            <h2>Retirada</h2>
            {bakery.address && <p>{bakery.address}</p>}
            {bakery.pickupInstructions && <p className={styles.muted}>{bakery.pickupInstructions}</p>}
          </article>
        )}
        {bakery?.whatsappLink && (
          <article className={styles.infoCard}>
            <h2>Dúvidas?</h2>
            <p>
              Fale com a gente pelo{' '}
              <a href={bakery.whatsappLink} target="_blank" rel="noopener noreferrer">
                WhatsApp {bakery.whatsappContact}
              </a>
              .
            </p>
          </article>
        )}
      </section>
    </PublicLayout>
  )
}
