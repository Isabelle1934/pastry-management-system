import type { ProblemDetail } from '../types/api'

export class ApiError extends Error {
  readonly status: number
  readonly problem?: ProblemDetail

  constructor(status: number, problem?: ProblemDetail) {
    super(problem?.detail ?? problem?.title ?? `Erro ${status}`)
    this.status = status
    this.problem = problem
  }

  get code(): string | undefined {
    return this.problem?.code
  }

  /** Erros por campo (validação), no formato { campo: mensagem }. */
  get fieldErrors(): Record<string, string> {
    const result: Record<string, string> = {}
    for (const error of this.problem?.errors ?? []) {
      result[error.field] ??= error.message
    }
    return result
  }
}

export const SESSION_EXPIRED_EVENT = 'auth:session-expired'

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((part) => part.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

/** Garante o cookie CSRF antes de uma requisição que altera dados. */
async function csrfToken(): Promise<string | undefined> {
  let token = readCookie(CSRF_COOKIE)
  if (!token) {
    await fetch('/api/public/csrf', { credentials: 'same-origin' })
    token = readCookie(CSRF_COOKIE)
  }
  return token ? decodeURIComponent(token) : undefined
}

export interface ApiRequestInit extends Omit<RequestInit, 'body'> {
  /** Objeto serializado como JSON. */
  json?: unknown
  body?: BodyInit
}

/** Cliente HTTP único da aplicação. Sempre relativo a /api (mesma origem), com cookies e CSRF. */
export async function apiFetch<T>(path: string, init: ApiRequestInit = {}): Promise<T> {
  const { json, headers: initHeaders, ...rest } = init
  const method = (init.method ?? (json !== undefined ? 'POST' : 'GET')).toUpperCase()
  const headers = new Headers(initHeaders)
  headers.set('Accept', 'application/json')

  let body = init.body
  if (json !== undefined) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(json)
  }
  if (!SAFE_METHODS.has(method)) {
    const token = await csrfToken()
    if (token) {
      headers.set(CSRF_HEADER, token)
    }
  }

  let response: Response
  try {
    response = await fetch(`/api${path}`, { ...rest, method, headers, body, credentials: 'same-origin' })
  } catch {
    throw new ApiError(0, {
      code: 'NETWORK_ERROR',
      detail: 'Não foi possível conectar ao servidor. Verifique sua internet e tente novamente.',
    })
  }

  if (!response.ok) {
    let problem: ProblemDetail | undefined
    try {
      problem = (await response.json()) as ProblemDetail
    } catch {
      problem = undefined
    }
    if (response.status === 401 && path.startsWith('/admin/') && !path.startsWith('/admin/auth/')) {
      // Sessão expirou durante o uso do painel: o AuthContext leva de volta ao login.
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    }
    throw new ApiError(response.status, problem)
  }

  if (response.status === 204 || response.status === 205) {
    return undefined as T
  }
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
