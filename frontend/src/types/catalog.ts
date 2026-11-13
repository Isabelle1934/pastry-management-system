/** Valores monetários chegam como string decimal ("120.00"). */
export type Money = string

export interface CakeSize {
  id: number
  name: string
  description: string | null
  servings: number | null
  basePrice: Money
  maxFlavors: number
  maxFillings: number
  active: boolean
  displayOrder: number
  version: number
  categoryId: number | null
  categoryName: string | null
  weightKg: Money | null
  diameterCm: number | null
}

export interface CakeSizeInput {
  name: string
  description: string
  servings: number | null
  basePrice: Money
  maxFlavors: number
  maxFillings: number
  displayOrder: number
  version?: number
  categoryId: number | null
  weightKg: Money | null
  diameterCm: number | null
}

/** Categoria de bolo (baixos, altos, 2 andares...). "consultOnly" = sem tabela, consulte valores. */
export interface CakeCategory {
  id: number
  name: string
  description: string | null
  notice: string | null
  consultOnly: boolean
  maxFlavors: number
  maxFillings: number
  active: boolean
  displayOrder: number
  sizeCount: number
  version: number
}

export interface CakeCategoryInput {
  name: string
  description: string
  notice: string
  consultOnly: boolean
  maxFlavors: number
  maxFillings: number
  displayOrder: number
  version?: number
}

/** Massa ou recheio. */
export interface CatalogOption {
  id: number
  name: string
  description: string | null
  active: boolean
  displayOrder: number
  version: number
}

export interface CatalogOptionInput {
  name: string
  description: string
  displayOrder: number
  version?: number
}

export type OptionKind = 'flavors' | 'fillings'
