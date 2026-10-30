import { useEffect, useState } from 'react'
import { getHealth } from '../services/health'

export type ApiStatus = 'checking' | 'online' | 'offline'

export function useApiStatus(): ApiStatus {
  const [status, setStatus] = useState<ApiStatus>('checking')

  useEffect(() => {
    let active = true
    getHealth()
      .then((health) => active && setStatus(health.status === 'UP' ? 'online' : 'offline'))
      .catch(() => active && setStatus('offline'))
    return () => {
      active = false
    }
  }, [])

  return status
}
