import type {
  Product,
  ProductCategory,
  ProductCategoryInput,
  ProductInput,
  ProductStatus,
} from '../types/product'
import { apiFetch } from './api'

const BASE = '/admin/catalog'

export function listCategories(): Promise<ProductCategory[]> {
  return apiFetch<ProductCategory[]>(`${BASE}/categories`)
}

export function saveCategory(input: ProductCategoryInput, id?: number): Promise<ProductCategory> {
  return id == null
    ? apiFetch<ProductCategory>(`${BASE}/categories`, { json: input })
    : apiFetch<ProductCategory>(`${BASE}/categories/${id}`, { method: 'PUT', json: input })
}

export function setCategoryActive(category: ProductCategory, active: boolean): Promise<ProductCategory> {
  return apiFetch<ProductCategory>(`${BASE}/categories/${category.id}/active`, {
    method: 'PATCH',
    json: { active, version: category.version },
  })
}

export function listProducts(): Promise<Product[]> {
  return apiFetch<Product[]>(`${BASE}/products`)
}

export function saveProduct(input: ProductInput, id?: number): Promise<Product> {
  return id == null
    ? apiFetch<Product>(`${BASE}/products`, { json: input })
    : apiFetch<Product>(`${BASE}/products/${id}`, { method: 'PUT', json: input })
}

export function setProductStatus(product: Product, status: ProductStatus): Promise<Product> {
  return apiFetch<Product>(`${BASE}/products/${product.id}/status`, {
    method: 'PATCH',
    json: { status, version: product.version },
  })
}

export function addProductPhoto(productId: number, photo: File): Promise<Product> {
  const form = new FormData()
  form.append('photo', photo)
  return apiFetch<Product>(`${BASE}/products/${productId}/photos`, { method: 'POST', body: form })
}

export function removeProductPhoto(productId: number, fileId: number): Promise<Product> {
  return apiFetch<Product>(`${BASE}/products/${productId}/photos/${fileId}`, { method: 'DELETE' })
}

/** Endereço da foto no painel (inclui produtos inativos). */
export function adminPhotoUrl(productId: number, fileId: number): string {
  return `/api${BASE}/products/${productId}/photos/${fileId}`
}

/** Endereço público da foto (somente produtos visíveis no catálogo). */
export function publicPhotoUrl(productId: number, fileId: number): string {
  return `/api/public/products/${productId}/photos/${fileId}`
}
