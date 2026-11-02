import type { CakeSize, CakeSizeInput, CatalogOption, CatalogOptionInput, OptionKind } from '../types/catalog'
import { apiFetch } from './api'

const BASE = '/admin/catalog'

export function listSizes(): Promise<CakeSize[]> {
  return apiFetch<CakeSize[]>(`${BASE}/sizes`)
}

export function saveSize(input: CakeSizeInput, id?: number): Promise<CakeSize> {
  return id == null
    ? apiFetch<CakeSize>(`${BASE}/sizes`, { json: input })
    : apiFetch<CakeSize>(`${BASE}/sizes/${id}`, { method: 'PUT', json: input })
}

export function setSizeActive(size: CakeSize, active: boolean): Promise<CakeSize> {
  return apiFetch<CakeSize>(`${BASE}/sizes/${size.id}/active`, {
    method: 'PATCH',
    json: { active, version: size.version },
  })
}

export function listOptions(kind: OptionKind): Promise<CatalogOption[]> {
  return apiFetch<CatalogOption[]>(`${BASE}/${kind}`)
}

export function saveOption(kind: OptionKind, input: CatalogOptionInput, id?: number): Promise<CatalogOption> {
  return id == null
    ? apiFetch<CatalogOption>(`${BASE}/${kind}`, { json: input })
    : apiFetch<CatalogOption>(`${BASE}/${kind}/${id}`, { method: 'PUT', json: input })
}

export function setOptionActive(kind: OptionKind, option: CatalogOption, active: boolean): Promise<CatalogOption> {
  return apiFetch<CatalogOption>(`${BASE}/${kind}/${option.id}/active`, {
    method: 'PATCH',
    json: { active, version: option.version },
  })
}
