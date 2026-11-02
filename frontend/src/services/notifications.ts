import { apiFetch } from './api'

export interface AdminNotification {
  id: number
  type: string
  title: string
  orderId: number | null
  message: string
  createdAt: string
  read: boolean
}

export function listNotifications(): Promise<AdminNotification[]> {
  return apiFetch<AdminNotification[]>('/admin/notifications?limit=50')
}

export function unreadNotificationCount(): Promise<{ count: number }> {
  return apiFetch<{ count: number }>('/admin/notifications/unread-count')
}

export function markNotificationRead(id: number): Promise<void> {
  return apiFetch<void>(`/admin/notifications/${id}/read`, { method: 'POST' })
}

export function markAllNotificationsRead(): Promise<void> {
  return apiFetch<void>('/admin/notifications/read-all', { method: 'POST' })
}
