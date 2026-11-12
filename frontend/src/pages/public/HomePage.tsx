import { Link } from 'react-router'
import { Alert } from '../../components/Alert'
import { Icon, type IconName } from '../../components/Icon'
import { PhotoSlot } from '../../components/PhotoSlot'
import { GALLERY, GALLERY_PLACEHOLDERS, HERO_PHOTO } from '../../content/site'
import { useBakeryInfo } from '../../hooks/useBakeryInfo'
import { PublicLayout } from '../../layouts/PublicLayout'
import motion from '../../styles/motion.module.css'
import styles from './public.module.css'

const STEPS: { icon: IconName; title: string; text: string }[] = [
  { icon: 'calendar', title: 'Escolha e agende', text: 'Escolha no cardápio ou personalize seu bolo e veja os dias livres.' },
  { icon: 'receipt', title: 'Receba o orçamento', text: 'Analisamos seu pedido e enviamos um orçamento personalizado.' },
  { icon: 'check', title: 'Confirme com o sinal', text: 'Com o orçamento aceito, o pedido é confirmado com 50% de sinal.' },
  { icon: 'bag', title: 'Retire ou receba', text: 'O restante é pago na retirada ou na entrega, no dia e horário combinados.' },
]

/** Página inicial: apresentação, como funciona, galeria e informações de retirada e contato. */
export function HomePage() {
  const { data: bakery, error } = useBakeryInfo()
  const gallery = GALLERY.length > 0 ? GALLERY : Array.from({ length: GALLERY_PLACEHOLDERS }, () => null)

  return (
    <PublicLayout wide>
      <section className={styles.hero}>
        <div className={`${styles.heroText} ${motion.stagger}`}>
          <p className={styles.eyebrow}>Bolos personalizados por encomenda</p>
          <h1 className={styles.heroTitle}>{bakery?.name ?? 'Encomende seu bolo'}</h1>
          {bakery?.aboutText && <p className={styles.about}>{bakery.aboutText}</p>}
          <div className={styles.heroActions}>
            <Link to="/encomendar" className={styles.cta}>
              Fazer encomenda <Icon name="arrowRight" size={18} />
            </Link>
            <Link to="/cardapio" className={styles.ctaGhost}>Ver cardápio</Link>
            <Link to="/acompanhar" className={styles.ctaGhost}>Já fez uma encomenda? Acompanhe</Link>
          </div>
        </div>
        <div className={`${styles.heroMedia} ${motion.fadeUp}`}>
          <PhotoSlot photo={HERO_PHOTO} placeholder="Foto de destaque de um bolo da confeitaria" className={styles.heroPhoto} />
        </div>
      </section>

      {error && <div className={styles.container}><Alert tone="error">{error}</Alert></div>}

      <section className={styles.band} aria-labelledby="how-title">
        <div className={styles.container}>
          <header className={styles.sectionHeader}>
            <p className={styles.eyebrow}>Simples assim</p>
            <h2 id="how-title" className={styles.sectionTitle}>Como funciona</h2>
          </header>
          <ol className={`${styles.steps} ${motion.stagger}`}>
            {STEPS.map((step, index) => (
              <li key={step.title} className={styles.step}>
                <span className={styles.stepIcon}><Icon name={step.icon} size={22} /></span>
                <span className={styles.stepNumber}>{String(index + 1).padStart(2, '0')}</span>
                <h3>{step.title}</h3>
                <p>{step.text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className={styles.section} aria-labelledby="gallery-title">
        <div className={styles.container}>
          <header className={styles.sectionHeader}>
            <p className={styles.eyebrow}>Galeria</p>
            <h2 id="gallery-title" className={styles.sectionTitle}>Feitos com carinho</h2>
          </header>
          <div className={styles.gallery}>
            {gallery.map((photo, index) => (
              <PhotoSlot key={photo?.src ?? index} photo={photo} placeholder="Foto de um bolo da confeitaria"
                className={styles.galleryItem} />
            ))}
          </div>
        </div>
      </section>

      {(bakery?.address || bakery?.pickupInstructions || bakery?.whatsappLink) && (
        <section className={styles.section} aria-labelledby="info-title">
          <div className={styles.container}>
            <h2 id="info-title" className={styles.visuallyHidden}>Retirada e contato</h2>
            <div className={styles.infoGrid}>
              {(bakery.address || bakery.pickupInstructions) && (
                <article className={styles.infoCard}>
                  <span className={styles.stepIcon}><Icon name="mapPin" size={22} /></span>
                  <h3>Retirada</h3>
                  {bakery.address && <p>{bakery.address}</p>}
                  {bakery.pickupInstructions && <p className={styles.muted}>{bakery.pickupInstructions}</p>}
                </article>
              )}
              {bakery.whatsappLink && (
                <article className={styles.infoCard}>
                  <span className={styles.stepIcon}><Icon name="message" size={22} /></span>
                  <h3>Dúvidas?</h3>
                  <p>
                    Fale com a gente pelo{' '}
                    <a href={bakery.whatsappLink} target="_blank" rel="noopener noreferrer">
                      WhatsApp {bakery.whatsappContact}
                    </a>
                    .
                  </p>
                </article>
              )}
            </div>
          </div>
        </section>
      )}

      <section className={styles.ctaBand}>
        <div className={styles.container}>
          <h2 className={styles.ctaTitle}>Vamos criar o seu bolo?</h2>
          <Link to="/encomendar" className={styles.ctaLight}>
            Fazer encomenda <Icon name="arrowRight" size={18} />
          </Link>
        </div>
      </section>
    </PublicLayout>
  )
}
