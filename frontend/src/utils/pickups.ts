import type { PickupItem } from '../types/order'

export interface PickupGroup {
  /** Data das retiradas do grupo; nula no grupo de atrasados. */
  date: string | null
  items: PickupItem[]
}

/** Agrupa a lista (já ordenada por data e horário) por dia, com os atrasados primeiro num grupo só. */
export function groupPickups(items: PickupItem[]): PickupGroup[] {
  const groups: PickupGroup[] = []
  const overdue = items.filter((item) => item.overdue)
  if (overdue.length > 0) groups.push({ date: null, items: overdue })
  for (const item of items) {
    if (item.overdue) continue
    const last = groups.at(-1)
    if (last && last.date === item.pickupDate) last.items.push(item)
    else groups.push({ date: item.pickupDate, items: [item] })
  }
  return groups
}
