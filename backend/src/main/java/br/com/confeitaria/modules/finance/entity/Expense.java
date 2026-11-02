package br.com.confeitaria.modules.finance.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import br.com.confeitaria.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Despesa geral ou vinculada a um pedido. É um registro único — o vínculo com o pedido é só um
 * atributo, por isso nunca entra duas vezes nos totais. Exclusão apenas lógica ({@code deletedAt}).
 */
@Entity
@Table(name = "expense")
public class Expense extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ExpenseCategory category;

    @Column(name = "order_id")
    private Long orderId;

    @Column(length = 300)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Expense() {
    }

    public Expense(String description, BigDecimal amount, LocalDate expenseDate, ExpenseCategory category,
            Long orderId, String notes) {
        update(description, amount, expenseDate, category, orderId, notes);
    }

    public final void update(String description, BigDecimal amount, LocalDate expenseDate, ExpenseCategory category,
            Long orderId, String notes) {
        this.description = description.strip();
        this.amount = Objects.requireNonNull(amount);
        this.expenseDate = Objects.requireNonNull(expenseDate);
        this.category = Objects.requireNonNull(category);
        this.orderId = orderId;
        this.notes = notes == null || notes.isBlank() ? null : notes.strip();
    }

    public void delete(Instant now) {
        this.deletedAt = Objects.requireNonNull(now);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
