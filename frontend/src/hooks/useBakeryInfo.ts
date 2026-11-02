import { getBakery } from '../services/publicApi'
import type { BakeryInfo } from '../types/public'
import { useLoader } from './useLoader'

let cached: Promise<BakeryInfo> | null = null

/** Busca as informações públicas uma vez por carregamento de página (compartilhadas entre layout e páginas). */
function loadBakeryOnce(): Promise<BakeryInfo> {
  cached ??= getBakery().catch((error: unknown) => {
    cached = null
    throw error
  })
  return cached
}

export function useBakeryInfo() {
  return useLoader(loadBakeryOnce)
}
