package br.com.confeitaria.modules.catalog.entity;

/** Situação do produto no catálogo. Nenhuma situação apaga dados. */
public enum ProductStatus {
    /** Aparece no catálogo público e pode ser escolhido (se aceitar encomendas). */
    ACTIVE,
    /** Aparece no catálogo como "indisponível no momento"; não pode ser escolhido. */
    UNAVAILABLE,
    /** Fora do catálogo público; pedidos antigos continuam com os dados originais. */
    INACTIVE
}
