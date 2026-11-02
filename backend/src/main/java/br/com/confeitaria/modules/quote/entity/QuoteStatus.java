package br.com.confeitaria.modules.quote.entity;

/** Situação do orçamento, independente da situação comercial do pedido. */
public enum QuoteStatus {
    /** Em elaboração pela proprietária; só ela vê. */
    DRAFT,
    /** Enviado à cliente, aguardando resposta até {@code validUntil}. */
    SENT,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    /** Substituído por uma versão mais nova (ou invalidado por edição da cliente). */
    SUPERSEDED
}
