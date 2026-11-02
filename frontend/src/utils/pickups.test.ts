import { describe, expect, it } from 'vitest'
import type { PickupItem } from '../types/order'
import { groupPickups } from './pickups'

function item(id: number, pickupDate: string, overdue = false): PickupItem {
  return {
    id, trackingCode: 'ABCD-1234-EFGH-5678', version: 0, customerName: `Cliente ${id}`,
    customerWhatsapp: '(11) 98765-4321', whatsappLink: 'https://wa.me/5511987654321', pickupDate, pickupTime: '14:00',
    sizeName: 'Médio', cakeText: null, topperRequested: false, productionStatus: 'NOT_STARTED',
    paymentStatus: 'DEPOSIT_PAID', total: '165.00', balanceDue: '82.50', overdue,
  }
}

describe('groupPickups', () => {
  it('groups consecutive orders by day and puts overdue orders first', () => {
    const groups = groupPickups([
      item(1, '2026-10-30', true),
      item(2, '2026-11-01', true),
      item(3, '2026-11-03'),
      item(4, '2026-11-03'),
      item(5, '2026-11-06'),
    ])
    expect(groups.map((g) => [g.date, g.items.map((i) => i.id)])).toEqual([
      [null, [1, 2]],
      ['2026-11-03', [3, 4]],
      ['2026-11-06', [5]],
    ])
  })

  it('returns no groups for an empty list', () => {
    expect(groupPickups([])).toEqual([])
  })
})
