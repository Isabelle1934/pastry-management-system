package br.com.confeitaria.modules.quote.entity;

/** Grupo do item no orçamento, usado para os subtotais (produtos, adicionais, topo de bolo e entrega). */
public enum QuoteItemKind {
    /** Bolo personalizado e produtos do catálogo. */
    PRODUCT,
    /** Valores extras (decoração especial, embalagem, ajustes). */
    EXTRA,
    /** Topo de bolo (fornecedor externo, cobrado à parte). */
    TOPPER,
    /** Taxa de entrega. */
    DELIVERY
}
