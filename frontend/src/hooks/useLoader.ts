import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../services/api'

interface LoaderState<T> {
  data: T | undefined
  loading: boolean
  error: string | undefined
  reload: () => Promise<void>
  /** Substitui os dados localmente (ex.: após salvar, sem nova requisição). */
  setData: (updater: (current: T | undefined) => T | undefined) => void
}

/** Carrega dados da API ao montar o componente, com estado de carregamento e erro. */
export function useLoader<T>(load: () => Promise<T>): LoaderState<T> {
  const [data, setDataState] = useState<T>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()

  const reload = useCallback(async () => {
    setLoading(true)
    setError(undefined)
    try {
      setDataState(await load())
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Não foi possível carregar os dados.')
    } finally {
      setLoading(false)
    }
  }, [load])

  useEffect(() => {
    let active = true
    load().then(
      (result) => {
        if (!active) return
        setDataState(result)
        setLoading(false)
      },
      (err: unknown) => {
        if (!active) return
        setError(err instanceof ApiError ? err.message : 'Não foi possível carregar os dados.')
        setLoading(false)
      },
    )
    return () => {
      active = false
    }
  }, [load])

  const setData = useCallback((updater: (current: T | undefined) => T | undefined) => setDataState(updater), [])

  return { data, loading, error, reload, setData }
}
