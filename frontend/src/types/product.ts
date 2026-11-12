import type { Money } from './catalog'

export type ProductStatus = 'ACTIVE' | 'UNAVAILABLE' | 'INACTIVE'
export type FulfillmentType = 'PICKUP' | 'DELIVERY'

export interface ProductCategory {
  id: number
  name: string
  description: string | null
  active: boolean
  displayOrder: number
  productCount: number
  version: number
}

export interface ProductCategoryInput {
  name: string
  description: string
  displayOrder: number
  version?: number
}

export interface Product {
  id: number
  categoryId: number
  categoryName: string
  name: string
  description: string | null
  /** `null` = preço sob orçamento. */
  price: Money | null
  unitLabel: string | null
  minQuantity: number
  status: ProductStatus
  orderable: boolean
  seasonal: boolean
  availableFrom: string | null
  availableUntil: string | null
  /** 1 = segunda … 7 = domingo; vazio = todos os dias. */
  availableDays: number[]
  pickupAllowed: boolean
  deliveryAllowed: boolean
  displayOrder: number
  photoIds: number[]
  version: number
  createdAt: string
  updatedAt: string
}

export interface ProductInput {
  categoryId: number
  name: string
  description: string
  price: Money | null
  unitLabel: string
  minQuantity: number
  orderable: boolean
  seasonal: boolean
  availableFrom: string | null
  availableUntil: string | null
  availableDays: number[]
  pickupAllowed: boolean
  deliveryAllowed: boolean
  displayOrder: number
  version?: number
}

export interface PublicCategory {
  id: number
  name: string
  description: string | null
}

export interface PublicProduct {
  id: number
  categoryId: number
  name: string
  description: string | null
  price: Money | null
  unitLabel: string | null
  minQuantity: number
  /** Pode ser escolhido agora (ativo e aceita encomendas pelo site). */
  available: boolean
  orderable: boolean
  seasonal: boolean
  availableFrom: string | null
  availableUntil: string | null
  availableDays: number[]
  pickupAllowed: boolean
  deliveryAllowed: boolean
  photoIds: number[]
}

/** Item do catálogo como foi pedido (cópia feita no momento da encomenda). */
export interface OrderItemView {
  productId: number
  name: string
  category: string
  unitPrice: Money | null
  unitLabel: string | null
  quantity: number
  notes: string | null
}

export interface OrderItemInput {
  productId: number
  quantity: number
  notes?: string
}
