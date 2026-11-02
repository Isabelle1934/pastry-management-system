import { createContext } from 'react'
import type { AdminUser } from '../types/auth'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export interface AuthContextValue {
  status: AuthStatus
  user: AdminUser | null
  login: (email: string, password: string) => Promise<AdminUser>
  logout: () => Promise<void>
  /** Recarrega a usuária da sessão (ex.: após trocar a senha). */
  refresh: () => Promise<AdminUser | null>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
