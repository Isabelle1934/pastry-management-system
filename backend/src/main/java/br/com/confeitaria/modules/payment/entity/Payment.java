package br.com.confeitaria.modules.payment.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.exception.ConflictException;
import br.com.confeitaria.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Pagamento de um pedido. Registros nunca são apagados (trigger no banco); um pagamento
 * analisado (confirmado ou rejeitado) não muda mais.
 */
@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

    public enum Kind {
        /** Sinal de 50% para confirmar o pedido. */
        DEPOSIT,
        /** Saldo pago na retirada. */
        BALANCE,
        /** Devolução registrada manualmente pela proprietária (ex.: cancelamento com sinal pago). */
        REFUND
    }

    public enum Method {
        PIX("Pix"),
        CASH("dinheiro"),
        /** Link de pagamento no cartão de crédito informado pela proprietária (sem integração automática). */
        CARD_LINK("cartão de crédito (link)");

        private final String label;

        Method(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Status {
        PENDING_REVIEW,
        CONFIRMED,
        REJECTED
    }

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Method method;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "proof_file_id", updatable = false)
    private Long proofFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "submitted_by", nullable = false, length = 20, updatable = false)
    private ActorType submittedBy;

    @Column(length = 300, updatable = false)
    private String notes;

    @Column(name = "review_note", length = 300)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected Payment() {
    }

    /** Comprovante enviado pela cliente, aguardando conferência. */
    public static Payment proofFromClient(Long orderId, Method method, BigDecimal amount, Long proofFileId) {
        Payment payment = new Payment(orderId, Kind.DEPOSIT, method, amount, ActorType.CLIENT, null);
        payment.status = Status.PENDING_REVIEW;
        payment.proofFileId = proofFileId;
        return payment;
    }

    /** Pagamento recebido e registrado pela própria proprietária (já conferido). */
    public static Payment registeredByOwner(Long orderId, Kind kind, Method method, BigDecimal amount, String notes,
            Instant now) {
        Payment payment = new Payment(orderId, kind, method, amount, ActorType.OWNER, notes);
        payment.status = Status.CONFIRMED;
        payment.reviewedAt = now;
        return payment;
    }

    private Payment(Long orderId, Kind kind, Method method, BigDecimal amount, ActorType submittedBy, String notes) {
        this.orderId = orderId;
        this.kind = kind;
        this.method = method;
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.submittedBy = submittedBy;
        this.notes = notes;
    }

    public void confirm(String note, Instant now) {
        requirePending();
        this.status = Status.CONFIRMED;
        this.reviewNote = note;
        this.reviewedAt = now;
    }

    public void reject(String reason, Instant now) {
        requirePending();
        this.status = Status.REJECTED;
        this.reviewNote = reason;
        this.reviewedAt = now;
    }

    private void requirePending() {
        if (status != Status.PENDING_REVIEW) {
            throw new ConflictException("PAYMENT_ALREADY_REVIEWED", "Este pagamento já foi analisado.");
        }
    }

    /** Valor com sinal para o saldo recebido: reembolsos subtraem. */
    public BigDecimal signedAmount() {
        return kind == Kind.REFUND ? amount.negate() : amount;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Kind getKind() {
        return kind;
    }

    public Method getMethod() {
        return method;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Status getStatus() {
        return status;
    }

    public Long getProofFileId() {
        return proofFileId;
    }

    public ActorType getSubmittedBy() {
        return submittedBy;
    }

    public String getNotes() {
        return notes;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
