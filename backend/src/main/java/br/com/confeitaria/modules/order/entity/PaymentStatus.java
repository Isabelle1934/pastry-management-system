package br.com.confeitaria.modules.order.entity;

/** Situação consolidada dos pagamentos do pedido (derivada dos registros de pagamento, Fase 8). */
public enum PaymentStatus {
    NONE,
    DEPOSIT_UNDER_REVIEW,
    DEPOSIT_PAID,
    PAID
}
