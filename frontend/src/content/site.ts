/**
 * Imagens da vitrine pública (logo, foto de destaque e galeria).
 *
 * Use SOMENTE fotos reais fornecidas pela proprietária. Coloque os arquivos em `frontend/public/fotos/`
 * e informe aqui o caminho (ex.: '/fotos/bolo-floral.jpg') e uma descrição curta para leitores de tela.
 * Enquanto uma lista estiver vazia, o site mostra espaços temporários identificados como tal.
 */
export interface SitePhoto {
  src: string
  alt: string
}

/** Logo (SVG ou PNG com fundo transparente). `null` = monograma com a inicial do nome da confeitaria. */
export const LOGO: SitePhoto | null = null

/** Foto principal da página inicial (formato vertical, de preferência 4:5). */
export const HERO_PHOTO: SitePhoto | null = null

/** Galeria de bolos (de 3 a 9 fotos; formato quadrado ou vertical). */
export const GALLERY: SitePhoto[] = []

/** Quantidade de espaços temporários mostrados enquanto a galeria estiver vazia. */
export const GALLERY_PLACEHOLDERS = 6
