import { ApiError } from '../../services/api'

export interface SaveFailure {
  /** Mensagem geral para exibir no topo do formulário. */
  message?: string
  /** Erros por campo vindos do backend. */
  fields: Record<string, string>
  /** O registro foi alterado em outra tela: recarregar a lista. */
  stale: boolean
}

export function describeSaveError(error: unknown): SaveFailure {
  if (!(error instanceof ApiError)) {
    return { message: 'Não foi possível salvar. Tente novamente.', fields: {}, stale: false }
  }
  const fields = error.fieldErrors
  return {
    message: Object.keys(fields).length > 0 ? undefined : error.message,
    fields,
    stale: error.code === 'CONCURRENT_MODIFICATION',
  }
}
