package br.com.confeitaria.modules.order.entity;

/** Modalidade da entrega (só para pedidos com delivery). Cada uma tem sua taxa nas Configurações. */
public enum DeliveryZone {
    /** Dentro da cidade: taxa de bolos ou de outros doces, conforme o pedido. */
    CITY,
    /** No local da festa: taxa própria, aplicada sozinha só depois de confirmada pela proprietária. */
    PARTY_VENUE,
    /** Fora da cidade: valor combinado conforme a localização, informado no orçamento. */
    OUTSIDE_CITY
}
