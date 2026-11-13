package br.com.confeitaria.modules.quote.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Linha do orçamento. O subtotal é sempre calculado aqui (e conferido por CHECK no banco). */
@Entity
@Table(name = "quote_item")
public class QuoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false, updatable = false)
    private Quote quote;

    @Column(nullable = false)
    private short position;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteItemKind kind = QuoteItemKind.PRODUCT;

    protected QuoteItem() {
    }

    QuoteItem(Quote quote, int position, QuoteItemKind kind, String name, String description, BigDecimal quantity,
            BigDecimal unitPrice) {
        this.quote = quote;
        this.kind = kind == null ? QuoteItemKind.PRODUCT : kind;
        this.position = (short) position;
        this.name = name;
        this.description = description;
        this.quantity = quantity.setScale(2, RoundingMode.HALF_UP);
        this.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        this.subtotal = this.quantity.multiply(this.unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public QuoteItemKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
