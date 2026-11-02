package br.com.confeitaria.modules.order.entity;

/** Situação comercial do pedido (BUSINESS_RULES.md §2). Independente de orçamento, pagamento e produção. */
public enum CommercialStatus {
    PENDING_REVIEW,
    QUOTE_SENT,
    AWAITING_DEPOSIT,
    CONFIRMED,
    COMPLETED,
    REJECTED,
    DECLINED_BY_CLIENT,
    CANCELLED
}
