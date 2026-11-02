import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { SESSION_EXPIRED_EVENT } from '../services/api'
import * as authService from '../services/auth'
import type { AdminUser } from '../types/auth'
import { AuthContext, type AuthStatus } from './auth-context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AdminUser | null>(null)
  const [status, setStatus] = useState<AuthStatus>('loading')

  const apply = useCallback((current: AdminUser | null) => {
    setUser(current)
    setStatus(current ? 'authenticated' : 'anonymous')
    return current
  }, [])

  const refresh = useCallback(
    () => authService.fetchCurrentAdmin().then(apply, () => apply(null)),
    [apply],
  )

  // Descobre, ao abrir o site, se já existe uma sessão válida.
  useEffect(() => {
    let active = true
    authService.fetchCurrentAdmin().then(
      (current) => active && apply(current),
      () => active && apply(null),
    )
    return () => {
      active = false
    }
  }, [apply])

  useEffect(() => {
    const onExpired = () => apply(null)
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired)
  }, [apply])

  const login = useCallback(
    async (email: string, password: string) => apply(await authService.login(email.trim(), password)) as AdminUser,
    [apply],
  )

  const logout = useCallback(async () => {
    try {
      await authService.logout()
    } finally {
      apply(null)
    }
  }, [apply])

  const value = useMemo(() => ({ status, user, login, logout, refresh }), [status, user, login, logout, refresh])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
