import type { Money } from './catalog'
import type { DeliveryZone, FulfillmentType, OrderItemInput, PublicCategory, PublicProduct } from './product'

export interface BakeryInfo {
  name: string | null
  aboutText: string | null
  whatsappContact: string | null
  whatsappLink: string | null
  instagram: string | null
  address: string | null
  pickupInstructions: string | null
  pickupEnabled: boolean
  deliveryEnabled: boolean
  /** Área atendida, taxa e prazos do delivery (só quando o delivery está ativo). */
  deliveryInfo: string | null
  /** Taxa de entrega de bolos dentro da cidade (só quando o delivery está ativo). */
  deliveryFee: Money | null
  customCakeEnabled: boolean
  /** Bolos personalizados também podem ser entregues. */
  customCakeDelivery: boolean
  /** Delivery de outros doces dentro da cidade. */
  deliveryFeeSweets: Money | null
  /** Entrega no local da festa — só vem depois de confirmada pela proprietária. */
  partyVenueFee: Money | null
  /** Aviso de pontualidade (editável pela proprietária). */
  punctualityNotice: string
}

/** Categoria de bolo (ex.: baixos, altos, 2 andares). "consultOnly" = sem tabela: consulte valores. */
export interface PublicCakeCategory {
  id: number
  name: string
  description: string | null
  notice: string | null
  consultOnly: boolean
  maxFlavors: number
  maxFillings: number
}

export interface PublicSize {
  id: number
  name: string
  description: string | null
  servings: number | null
  basePrice: Money
  maxFlavors: number
  maxFillings: number
  categoryId: number | null
  weightKg: Money | null
  diameterCm: number | null
}

export interface PublicOption {
  id: number
  name: string
  description: string | null
}

export interface PublicCatalog {
  sizes: PublicSize[]
  flavors: PublicOption[]
  fillings: PublicOption[]
  categories: PublicCategory[]
  products: PublicProduct[]
  /** Aceita novas encomendas de bolo personalizado. */
  customCakeEnabled: boolean
  cakeCategories: PublicCakeCategory[]
}

export interface AvailableDay {
  date: string
  times: string[]
}

export interface Availability {
  from: string
  to: string
  days: AvailableDay[]
}

/** Corpo JSON da parte "order" do envio multipart. */
export interface OrderPayload {
  customerName: string
  customerWhatsapp: string
  pickupDate: string
  pickupTime: string
  customCake: boolean
  sizeId: number | null
  flavorIds: number[]
  fillingIds: number[]
  decoration: string
  cakeText: string
  notes: string
  topperRequested: boolean
  topperTheme: string
  topperName: string
  topperAge: number | null
  items: OrderItemInput[]
  fulfillmentType: FulfillmentType
  deliveryAddress: string
  deliveryDistrict: string
  deliveryReference: string
  cakeCategoryId: number | null
  deliveryZone: DeliveryZone | null
  customerEmail: string
}
