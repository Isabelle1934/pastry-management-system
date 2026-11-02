package br.com.confeitaria.modules.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.validation.WhatsAppNumber;
import br.com.confeitaria.modules.file.entity.FilePurpose;
import br.com.confeitaria.modules.file.entity.StoredFile;
import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.order.entity.CommercialStatus;
import br.com.confeitaria.modules.order.entity.OrderHistory;
import br.com.confeitaria.modules.order.entity.PaymentStatus;
import br.com.confeitaria.modules.order.entity.ProductionStatus;
import br.com.confeitaria.modules.order.service.TrackingCodes;
import br.com.confeitaria.modules.payment.dto.PaymentDtos.PaymentSummary;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.QuoteView;
import br.com.confeitaria.modules.quote.entity.Quote;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos da gestão de pedidos no painel. */
public final class OrderAdminDtos {

    private OrderAdminDtos() {
    }

    public record OrderSummary(Long id, String trackingCode, String customerName, String customerWhatsapp,
            LocalDate pickupDate, String pickupTime, String sizeName, CommercialStatus commercialStatus,
            PaymentStatus paymentStatus, ProductionStatus productionStatus, BigDecimal quoteTotal, Instant createdAt) {

        public static OrderSummary from(CakeOrder order, Quote currentQuote) {
            return new OrderSummary(order.getId(), TrackingCodes.format(order.getTrackingCode()), order.getCustomerName(),
                    WhatsAppNumber.format(order.getCustomerWhatsapp()), order.getPickupDate(),
                    order.getPickupTime().toString(), order.getSizeNameSnapshot(), order.getCommercialStatus(),
                    order.getPaymentStatus(), order.getProductionStatus(),
                    currentQuote == null ? null : currentQuote.getTotal(), order.getCreatedAt());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    public record FileInfo(Long id, FilePurpose purpose, String originalName, String contentType, long sizeBytes,
            ActorType uploadedBy, Instant createdAt) {

        public static FileInfo from(StoredFile file) {
            return new FileInfo(file.getId(), file.getPurpose(), file.getOriginalName(), file.getContentType(),
                    file.getSizeBytes(), file.getUploadedBy(), file.getCreatedAt());
        }
    }

    public record HistoryEntry(String eventType, ActorType actor, String description, Map<String, Object> details,
            Instant createdAt) {

        public static HistoryEntry from(OrderHistory entry) {
            return new HistoryEntry(entry.getEventType(), entry.getActor(), entry.getDescription(), entry.getDetails(),
                    entry.getCreatedAt());
        }
    }

    public record Choice(Long id, String name) {
    }

    /** Detalhe completo do pedido para a proprietária. */
    public record OrderDetail(Long id, String trackingCode, long version, String customerName, String customerWhatsapp,
            String whatsappLink, LocalDate pickupDate, String pickupTime, Long sizeId, String sizeName,
            List<Choice> flavors, List<Choice> fillings, String decoration, String cakeText, String notes,
            boolean topperRequested, String topperTheme, String topperName, Short topperAge,
            CommercialStatus commercialStatus, PaymentStatus paymentStatus, ProductionStatus productionStatus,
            Instant cancelledAt, String cancellationReason, ActorType cancelledBy, boolean slotReserved,
            List<FileInfo> files, List<QuoteView> quotes, PaymentSummary payments, List<HistoryEntry> history,
            Instant createdAt, Instant updatedAt) {
    }

    public record ReasonRequest(
            @NotBlank(message = "Informe o motivo.") @Size(max = 300, message = "Máximo de 300 caracteres.") String reason,
            @NotNull(message = "Versão obrigatória.") Long version) {
    }
}
