package br.com.confeitaria.modules.quote.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import br.com.confeitaria.common.exception.ConflictException;
import br.com.confeitaria.common.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Orçamento de um pedido. Editável apenas como rascunho; depois de enviado, os valores ficam
 * congelados (também no banco, por trigger) e qualquer mudança exige uma nova versão.
 */
@Entity
@Table(name = "quote")
public class Quote extends BaseEntity {

    /** Sinal de 50% (regra fixa do negócio). */
    public static final BigDecimal DEPOSIT_RATE = new BigDecimal("0.50");

    public record ItemData(QuoteItemKind kind, String name, String description, BigDecimal quantity,
            BigDecimal unitPrice) {
    }

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteStatus status = QuoteStatus.DRAFT;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO.setScale(2);

    @Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO.setScale(2);

    @Column(length = 1000)
    private String notes;

    @Column(name = "validity_hours", nullable = false)
    private int validityHours;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "decline_reason", length = 300)
    private String declineReason;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<QuoteItem> items = new ArrayList<>();

    protected Quote() {
    }

    public Quote(Long orderId, int versionNumber) {
        this.orderId = orderId;
        this.versionNumber = versionNumber;
    }

    /** Substitui itens, observações e validade do rascunho e recalcula total e sinal. */
    public void editDraft(List<ItemData> newItems, String notes, int validityHours) {
        requireStatus(QuoteStatus.DRAFT, "Somente rascunhos podem ser editados.");
        items.clear();
        int position = 0;
        for (ItemData item : newItems) {
            items.add(new QuoteItem(this, position++, item.kind(), item.name(), item.description(), item.quantity(),
                    item.unitPrice()));
        }
        this.notes = notes;
        this.validityHours = validityHours;
        this.total = items.stream().map(QuoteItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        this.depositAmount = depositFor(total);
    }

    /** Sinal = 50% do total em centavos (HALF_UP); o saldo é sempre total − sinal, fechando o valor exato. */
    public static BigDecimal depositFor(BigDecimal total) {
        return total.multiply(DEPOSIT_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public void send(Instant now) {
        requireStatus(QuoteStatus.DRAFT, "Este orçamento já foi enviado.");
        if (items.isEmpty() || total.signum() <= 0) {
            throw new ConflictException("QUOTE_EMPTY", "Adicione itens com valor antes de enviar o orçamento.");
        }
        this.status = QuoteStatus.SENT;
        this.sentAt = now;
        this.validUntil = now.plus(Duration.ofHours(validityHours));
    }

    public boolean isExpiredAt(Instant now) {
        return status == QuoteStatus.SENT && !now.isBefore(validUntil);
    }

    public void accept(Instant now) {
        requireStatus(QuoteStatus.SENT, "Este orçamento não está aguardando resposta.");
        this.status = QuoteStatus.ACCEPTED;
        this.respondedAt = now;
    }

    public void decline(String reason, Instant now) {
        requireStatus(QuoteStatus.SENT, "Este orçamento não está aguardando resposta.");
        this.status = QuoteStatus.DECLINED;
        this.declineReason = reason;
        this.respondedAt = now;
    }

    public void supersede() {
        if (status != QuoteStatus.SENT && status != QuoteStatus.ACCEPTED) {
            throw new ConflictException("INVALID_QUOTE_STATUS", "Este orçamento não está vigente.");
        }
        this.status = QuoteStatus.SUPERSEDED;
    }

    public void expire(Instant now) {
        requireStatus(QuoteStatus.SENT, "Este orçamento não está aguardando resposta.");
        this.status = QuoteStatus.EXPIRED;
        this.expiredAt = now;
    }

    private void requireStatus(QuoteStatus expected, String message) {
        if (status != expected) {
            throw new ConflictException("INVALID_QUOTE_STATUS", message);
        }
    }

    public Long getOrderId() {
        return orderId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public QuoteStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public BigDecimal getBalanceAmount() {
        return total.subtract(depositAmount);
    }

    public String getNotes() {
        return notes;
    }

    public int getValidityHours() {
        return validityHours;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }

    public List<QuoteItem> getItems() {
        return List.copyOf(items);
    }
}
