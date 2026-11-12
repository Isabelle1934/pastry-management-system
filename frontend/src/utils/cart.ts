/**
 * Seleção feita no catálogo público, levada para o formulário de encomenda. Fica só nesta aba
 * (sessionStorage) e não contém dados pessoais. Se o navegador bloquear o armazenamento, a seleção
 * simplesmente não é lembrada.
 */
import type { OrderItemInput } from '../types/product'

const KEY = 'confeitaria:selecao'

export function readCart(): OrderItemInput[] {
  try {
    const raw = window.sessionStorage.getItem(KEY)
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((i): i is OrderItemInput =>
        typeof i === 'object' && i !== null && Number.isInteger((i as OrderItemInput).productId) &&
        Number.isInteger((i as OrderItemInput).quantity) && (i as OrderItemInput).quantity > 0)
      .map((i) => ({ productId: i.productId, quantity: i.quantity }))
  } catch {
    return []
  }
}

export function writeCart(items: OrderItemInput[]): void {
  try {
    if (items.length === 0) window.sessionStorage.removeItem(KEY)
    else window.sessionStorage.setItem(KEY, JSON.stringify(items.map(({ productId, quantity }) => ({ productId, quantity }))))
  } catch {
    // Armazenamento indisponível: a seleção só não é lembrada.
  }
}

/** Define a quantidade de um produto (0 remove). */
export function setQuantity(items: OrderItemInput[], productId: number, quantity: number): OrderItemInput[] {
  if (quantity <= 0) return items.filter((i) => i.productId !== productId)
  return items.some((i) => i.productId === productId)
    ? items.map((i) => (i.productId === productId ? { ...i, quantity } : i))
    : [...items, { productId, quantity }]
}
