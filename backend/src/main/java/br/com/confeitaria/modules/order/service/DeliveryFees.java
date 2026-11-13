package br.com.confeitaria.modules.order.service;

import java.math.BigDecimal;

import br.com.confeitaria.modules.order.entity.DeliveryZone;
import br.com.confeitaria.modules.order.entity.FulfillmentType;
import br.com.confeitaria.modules.settings.entity.BakerySettings;

/**
 * Taxa de entrega sugerida para o orçamento, conforme a modalidade (BUSINESS_RULES.md §10.1):
 * <ul>
 * <li>na cidade, com bolo personalizado: taxa de entrega de bolos;</li>
 * <li>na cidade, só outros doces: taxa de delivery de doces;</li>
 * <li>no local da festa: taxa própria, só depois de confirmada pela proprietária;</li>
 * <li>fora da cidade: sem sugestão — valor combinado e informado no orçamento.</li>
 * </ul>
 * A sugestão nunca é aplicada sem a proprietária ver: ela entra no rascunho do orçamento, que é editável.
 */
public final class DeliveryFees {

    private DeliveryFees() {
    }

    /** @return a taxa sugerida, ou {@code null} quando não há valor automático (retirada, fora da cidade etc.) */
    public static BigDecimal suggested(BakerySettings settings, FulfillmentType type, DeliveryZone zone,
            boolean customCake) {
        if (type != FulfillmentType.DELIVERY || zone == null) {
            return null;
        }
        return switch (zone) {
            case CITY -> customCake ? settings.getDeliveryFee() : settings.getDeliveryFeeSweets();
            case PARTY_VENUE -> settings.isPartyVenueFeeConfirmed() ? settings.getPartyVenueFee() : null;
            case OUTSIDE_CITY -> null;
        };
    }
}
