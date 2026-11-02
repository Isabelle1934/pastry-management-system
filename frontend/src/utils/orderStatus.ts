import type { CommercialStatus, PaymentStatus, ProductionStatus, QuoteStatus } from '../types/order'

export type Tone = 'ok' | 'warn' | 'neutral' | 'info'

export const COMMERCIAL_STATUS: Record<CommercialStatus, { label: string; tone: Tone }> = {
  PENDING_REVIEW: { label: 'Aguardando análise', tone: 'info' },
  QUOTE_SENT: { label: 'Orçamento enviado', tone: 'neutral' },
  AWAITING_DEPOSIT: { label: 'Aguardando sinal', tone: 'info' },
  CONFIRMED: { label: 'Confirmado', tone: 'ok' },
  COMPLETED: { label: 'Entregue', tone: 'ok' },
  REJECTED: { label: 'Recusado', tone: 'warn' },
  DECLINED_BY_CLIENT: { label: 'Orçamento recusado pela cliente', tone: 'warn' },
  CANCELLED: { label: 'Cancelado', tone: 'warn' },
}

export const PAYMENT_STATUS: Record<PaymentStatus, string> = {
  NONE: 'Sem pagamento',
  DEPOSIT_UNDER_REVIEW: 'Comprovante em análise',
  DEPOSIT_PAID: 'Sinal pago',
  PAID: 'Pago',
}

export const PRODUCTION_STATUS: Record<ProductionStatus, string> = {
  NOT_STARTED: 'Não iniciada',
  IN_PRODUCTION: 'Em produção',
  READY: 'Pronto',
  DELIVERED: 'Entregue',
}

export const QUOTE_STATUS: Record<QuoteStatus, { label: string; tone: Tone }> = {
  DRAFT: { label: 'Rascunho', tone: 'neutral' },
  SENT: { label: 'Enviado, aguardando resposta', tone: 'info' },
  ACCEPTED: { label: 'Aceito', tone: 'ok' },
  DECLINED: { label: 'Recusado', tone: 'warn' },
  EXPIRED: { label: 'Vencido', tone: 'warn' },
  SUPERSEDED: { label: 'Substituído', tone: 'neutral' },
}

/** Ordem dos filtros na lista de pedidos (os que pedem ação primeiro). */
export const STATUS_FILTER_ORDER: CommercialStatus[] = [
  'PENDING_REVIEW',
  'QUOTE_SENT',
  'AWAITING_DEPOSIT',
  'CONFIRMED',
  'COMPLETED',
  'DECLINED_BY_CLIENT',
  'REJECTED',
  'CANCELLED',
]

/** "2026-11-03T15:00:00Z" → "03/11/2026 às 12:00" (fuso da confeitaria). */
export function formatDateTime(iso: string | null): string {
  if (!iso) return ''
  const parts = new Intl.DateTimeFormat('pt-BR', {
    timeZone: 'America/Sao_Paulo',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).formatToParts(new Date(iso))
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
  return `${get('day')}/${get('month')}/${get('year')} às ${get('hour')}:${get('minute')}`
}
