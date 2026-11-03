import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { Loading } from '../components/Loading'
import { useAuth } from '../hooks/useAuth'

const CHANGE_PASSWORD_PATH = '/admin/trocar-senha'

/**
 * Exige sessão para o painel. É só conveniência de navegação: a proteção real
 * está no backend, que recusa qualquer chamada /api/admin sem sessão válida.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status, user } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <Loading />
  if (status === 'anonymous') {
    return <Navigate to="/admin/login" replace state={{ from: location.pathname }} />
  }
  if (user?.mustChangePassword && location.pathname !== CHANGE_PASSWORD_PATH) {
    return <Navigate to={CHANGE_PASSWORD_PATH} replace />
  }
  return children
}

/** Telas de login/recuperação: quem já está conectada vai direto para o painel. */
export function GuestOnly({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  if (status === 'loading') return <Loading />
  if (status === 'authenticated') return <Navigate to="/admin" replace />
  return children
}
