/** Formato de erro da API (RFC 9457 — application/problem+json). */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  code?: string
  errors?: { field: string; message: string }[]
}
