package br.com.confeitaria.modules.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.modules.order.entity.PaymentStatus;
import br.com.confeitaria.modules.payment.entity.Payment;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PaymentView(Long id, Payment.Kind kind, Payment.Method method, BigDecimal amount,
            Payment.Status status, Long proofFileId, ActorType submittedBy, String notes, String reviewNote,
            Instant reviewedAt, Instant createdAt) {

        public static PaymentView from(Payment p) {
            return new PaymentView(p.getId(), p.getKind(), p.getMethod(), p.getAmount(), p.getStatus(),
                    p.getProofFileId(), p.getSubmittedBy(), p.getNotes(), p.getReviewNote(), p.getReviewedAt(),
                    p.getCreatedAt());
        }
    }

    /**
     * Resumo financeiro do pedido. Valores nulos quando ainda não há orçamento aceito.
     *
     * @param received   soma dos pagamentos confirmados, menos reembolsos
     * @param balanceDue total − recebido (nunca negativo)
     */
    /** {@code depositDueAt}: fim do prazo do sinal, só enquanto o pedido aguarda o sinal (null = sem prazo). */
    public record PaymentSummary(BigDecimal total, BigDecimal depositAmount, BigDecimal received, BigDecimal balanceDue,
            PaymentStatus status, boolean proofUnderReview, Instant depositDueAt, List<PaymentView> payments) {
    }

    public record RegisterRequest(
            @NotNull(message = "Informe o tipo de pagamento.") Payment.Kind kind,
            @NotNull(message = "Informe a forma de pagamento.") Payment.Method method,
            @NotNull(message = "Informe o valor.")
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
            @DecimalMax(value = "99999.99", message = "Valor muito alto.")
            @Digits(integer = 5, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal amount,
            @Size(max = 300, message = "Máximo de 300 caracteres.") String notes,
            Boolean acceptAsDeposit) {

        public boolean acceptsAsDeposit() {
            return Boolean.TRUE.equals(acceptAsDeposit);
        }
    }

    /** {@code acceptAsDeposit}: confirmar o pedido mesmo que o valor seja menor que o sinal combinado. */
    public record ConfirmRequest(@Size(max = 300) String note, Boolean acceptAsDeposit) {

        public boolean acceptsAsDeposit() {
            return Boolean.TRUE.equals(acceptAsDeposit);
        }
    }

    public record RejectRequest(
            @NotBlank(message = "Informe o motivo para a cliente.") @Size(max = 300, message = "Máximo de 300 caracteres.")
            String reason) {
    }
}
