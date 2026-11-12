import type { Money } from './catalog'
import type { FulfillmentType, OrderItemInput, PublicCategory, PublicProduct } from './product'

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
  customCakeEnabled: boolean
  /** Bolos personalizados também podem ser entregues. */
  customCakeDelivery: boolean
}

export interface PublicSize {
  id: number
  name: string
  description: string | null
  servings: number | null
  basePrice: Money
  maxFlavors: number
  maxFillings: number
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
}
