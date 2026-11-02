import type { Money } from './catalog'

export interface BakeryInfo {
  name: string | null
  aboutText: string | null
  whatsappContact: string | null
  whatsappLink: string | null
  instagram: string | null
  address: string | null
  pickupInstructions: string | null
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
  sizeId: number
  flavorIds: number[]
  fillingIds: number[]
  decoration: string
  cakeText: string
  notes: string
  topperRequested: boolean
  topperTheme: string
  topperName: string
  topperAge: number | null
}
