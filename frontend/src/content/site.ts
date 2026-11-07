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

/*
 * ATENÇÃO — FOTOS SOMENTE PARA TESTE: os arquivos `teste-*` abaixo não são da confeitaria
 * (um deles parece gerado por IA). Substitua por fotos reais da proprietária ANTES de publicar o site.
 */

/** Foto principal da página inicial (formato vertical, de preferência 4:5). */
export const HERO_PHOTO: SitePhoto | null = {
  src: '/fotos/teste-bolo-brigadeiros-morangos.jpg',
  alt: 'Bolo de chocolate com cobertura de chocolate branco escorrendo, brigadeiros e morangos',
}

/** Galeria de bolos (de 3 a 9 fotos; formato quadrado ou vertical). */
export const GALLERY: SitePhoto[] = [
  {
    src: '/fotos/teste-bolo-morangos-chantilly.jpg',
    alt: 'Bolo branco com chocolate escorrendo, rosetas de creme e chocolate e morangos no topo',
  },
  {
    src: '/fotos/teste-bolo-brigadeiros-ganache.jpg',
    alt: 'Bolo de chocolate com cobertura branca, brigadeiros e fios de chocolate',
  },
  {
    src: '/fotos/teste-bolo-kitkat.jpg',
    alt: 'Bolo cercado por barras de chocolate, com confeitos coloridos no topo e laço de sisal',
  },
  {
    src: '/fotos/teste-bolo-cerejas.webp',
    alt: 'Bolo em camadas com calda de chocolate, cerejas, pedaços de chocolate e creme de caramelo',
  },
]

/** Quantidade de espaços temporários mostrados enquanto a galeria estiver vazia. */
export const GALLERY_PLACEHOLDERS = 6
