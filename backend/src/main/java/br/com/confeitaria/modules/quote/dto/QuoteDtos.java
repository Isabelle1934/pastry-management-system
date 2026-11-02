package br.com.confeitaria.modules.quote.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import br.com.confeitaria.modules.quote.entity.Quote;
import br.com.confeitaria.modules.quote.entity.QuoteItem;
import br.com.confeitaria.modules.quote.entity.QuoteStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class QuoteDtos {

    private QuoteDtos() {
    }

    public record ItemRequest(
            @NotBlank(message = "Informe o nome do item.") @Size(max = 120, message = "Máximo de 120 caracteres.") String name,
            @Size(max = 300, message = "Máximo de 300 caracteres.") String description,
            @NotNull(message = "Informe a quantidade.")
            @DecimalMin(value = "0.01", message = "A quantidade deve ser maior que zero.")
            @DecimalMax(value = "9999", message = "Quantidade muito alta.")
            @Digits(integer = 4, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal quantity,
            @NotNull(message = "Informe o valor.")
            @DecimalMin(value = "0.00", message = "O valor não pode ser negativo.")
            @DecimalMax(value = "99999.99", message = "Valor muito alto.")
            @Digits(integer = 5, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal unitPrice) {
    }

    /** {@code validityHours} vazio = validade padrão das Configurações. */
    public record DraftRequest(
            @NotEmpty(message = "Adicione pelo menos um item.") @Size(max = 30, message = "Máximo de 30 itens.")
            List<@Valid ItemRequest> items,
            @Size(max = 1000, message = "Máximo de 1000 caracteres.") String notes,
            @Min(value = 1, message = "Mínimo de 1 hora.") @Max(value = 720, message = "Máximo de 720 horas.") Integer validityHours) {
    }

    public record ItemView(String name, String description, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal subtotal) {

        static ItemView from(QuoteItem item) {
            return new ItemView(item.getName(), item.getDescription(), item.getQuantity(), item.getUnitPrice(),
                    item.getSubtotal());
        }
    }

    public record QuoteView(Long id, int versionNumber, QuoteStatus status, List<ItemView> items, BigDecimal total,
            BigDecimal depositAmount, BigDecimal balanceAmount, String notes, int validityHours, Instant validUntil,
            Instant sentAt, Instant respondedAt, String declineReason, Instant expiredAt, Instant createdAt) {

        public static QuoteView from(Quote quote) {
            return new QuoteView(quote.getId(), quote.getVersionNumber(), quote.getStatus(),
                    quote.getItems().stream().map(ItemView::from).toList(), quote.getTotal(), quote.getDepositAmount(),
                    quote.getBalanceAmount(), quote.getNotes(), quote.getValidityHours(), quote.getValidUntil(),
                    quote.getSentAt(), quote.getRespondedAt(), quote.getDeclineReason(), quote.getExpiredAt(),
                    quote.getCreatedAt());
        }
    }

    public record DeclineRequest(@Size(max = 300, message = "Máximo de 300 caracteres.") String reason) {
    }
}
