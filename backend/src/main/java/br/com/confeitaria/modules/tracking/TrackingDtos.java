package br.com.confeitaria.modules.tracking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import br.com.confeitaria.modules.order.dto.OrderAdminDtos.Choice;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.OrderItemView;
import br.com.confeitaria.modules.order.entity.FulfillmentType;
import br.com.confeitaria.modules.order.entity.CommercialStatus;
import br.com.confeitaria.modules.order.entity.PaymentStatus;
import br.com.confeitaria.modules.order.entity.ProductionStatus;
import br.com.confeitaria.modules.payment.entity.Payment;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.ItemView;
import br.com.confeitaria.modules.quote.entity.QuoteStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class TrackingDtos {

    private TrackingDtos() {
    }

    public record VerifyRequest(
            @NotBlank(message = "Informe o código de acompanhamento.") @Size(max = 40) String trackingCode,
            @NotBlank(message = "Informe os 4 últimos dígitos do WhatsApp.")
            @Pattern(regexp = "\\d{4}", message = "Informe os 4 últimos dígitos do WhatsApp.") String whatsappLast4) {
    }

    /** Orçamento como a cliente o vê (só a versão vigente). */
    public record ClientQuote(Long id, int versionNumber, QuoteStatus status, List<ItemView> items, BigDecimal total,
            BigDecimal depositAmount, BigDecimal balanceAmount, String notes, Instant validUntil) {
    }

    public record ClientPaymentEntry(Payment.Kind kind, BigDecimal amount, Payment.Status status, String reviewNote,
            Instant createdAt, Long proofFileId) {
    }

    /** {@code pixKey} só é preenchida quando há algo a pagar. */
    public record ClientPayment(BigDecimal total, BigDecimal depositAmount, BigDecimal received, BigDecimal balanceDue,
            PaymentStatus status, String pixKey, String pixHolderName, boolean canSubmitProof,
            List<ClientPaymentEntry> payments) {
    }

    /**
     * Visão da cliente: sem dados internos (ids do banco, histórico, observações da proprietária).
     * O WhatsApp aparece mascarado.
     */
    public record TrackingView(String trackingCode, String customerName, String whatsappMasked, LocalDate pickupDate,
            String pickupTime, boolean customCake, List<OrderItemView> items, FulfillmentType fulfillmentType,
            String deliveryAddress, String deliveryDistrict, String deliveryReference, Long sizeId, String sizeName, List<Choice> flavors, List<Choice> fillings,
            String decoration, String cakeText, String notes, boolean topperRequested, String topperTheme,
            String topperName, Short topperAge, int photoCount, CommercialStatus commercialStatus,
            PaymentStatus paymentStatus, ProductionStatus productionStatus, boolean editable, long version,
            ClientQuote quote, ClientPayment payment) {
    }
}
