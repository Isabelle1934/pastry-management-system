import type { AdminUser } from '../types/auth'
import { ApiError, apiFetch } from './api'

/** Usuária da sessão atual, ou null se não houver sessão. */
export async function fetchCurrentAdmin(): Promise<AdminUser | null> {
  try {
    return await apiFetch<AdminUser>('/admin/auth/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

export function login(email: string, password: string): Promise<AdminUser> {
  return apiFetch<AdminUser>('/admin/auth/login', { json: { email, password } })
}

export function logout(): Promise<void> {
  return apiFetch<void>('/admin/auth/logout', { method: 'POST' })
}

export function requestPasswordReset(email: string): Promise<{ message: string }> {
  return apiFetch<{ message: string }>('/admin/auth/password/forgot', { json: { email } })
}

export function resetPassword(token: string, newPassword: string): Promise<void> {
  return apiFetch<void>('/admin/auth/password/reset', { json: { token, newPassword } })
}

export function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  return apiFetch<void>('/admin/auth/password/change', { json: { currentPassword, newPassword } })
}
