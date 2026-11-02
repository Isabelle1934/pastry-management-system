package br.com.confeitaria.modules.production;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import br.com.confeitaria.modules.order.entity.PaymentStatus;
import br.com.confeitaria.modules.order.entity.ProductionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos do andamento da produção e da lista de próximas retiradas. */
public final class ProductionDtos {

    private ProductionDtos() {
    }

    /**
     * @param confirmBalanceDue obrigatório para entregar com saldo em aberto (P8: permitido, com aviso)
     */
    public record ChangeRequest(
            @NotNull(message = "Informe o andamento.") ProductionStatus status,
            @NotNull(message = "Versão obrigatória.") Long version,
            @Size(max = 300, message = "Máximo de 300 caracteres.") String note,
            Boolean confirmBalanceDue) {

        public boolean confirmsBalanceDue() {
            return Boolean.TRUE.equals(confirmBalanceDue);
        }
    }

    /** Pedido confirmado na lista de retiradas. {@code overdue}: a data de retirada já passou sem entrega. */
    public record PickupItem(Long id, String trackingCode, long version, String customerName, String customerWhatsapp,
            String whatsappLink, LocalDate pickupDate, String pickupTime, String sizeName, String cakeText,
            boolean topperRequested, ProductionStatus productionStatus, PaymentStatus paymentStatus, BigDecimal total,
            BigDecimal balanceDue, boolean overdue) {
    }

    public record PickupBoard(LocalDate today, LocalDate until, List<PickupItem> orders) {
    }
}
