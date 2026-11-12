package br.com.confeitaria.modules.order.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Produto do catálogo escolhido no pedido. Nome, categoria, preço e unidade são copiados no momento
 * da encomenda: alterações posteriores no catálogo não mudam o que foi pedido.
 */
@Embeddable
public class OrderItem {

    @Column(name = "name_snapshot", nullable = false, length = 100)
    private String nameSnapshot;

    @Column(name = "category_snapshot", nullable = false, length = 60)
    private String categorySnapshot;

    @Column(name = "unit_price_snapshot", precision = 10, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "unit_label_snapshot", length = 40)
    private String unitLabelSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 300)
    private String notes;

    @Column(nullable = false)
    private short position;

    protected OrderItem() {
    }

    public OrderItem(String nameSnapshot, String categorySnapshot, BigDecimal unitPriceSnapshot,
            String unitLabelSnapshot, int quantity, String notes, int position) {
        this.nameSnapshot = nameSnapshot;
        this.categorySnapshot = categorySnapshot;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.unitLabelSnapshot = unitLabelSnapshot;
        this.quantity = quantity;
        this.notes = notes;
        this.position = (short) position;
    }

    public String getNameSnapshot() {
        return nameSnapshot;
    }

    public String getCategorySnapshot() {
        return categorySnapshot;
    }

    public BigDecimal getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public String getUnitLabelSnapshot() {
        return unitLabelSnapshot;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getNotes() {
        return notes;
    }

    public int getPosition() {
        return position;
    }

    /** Texto curto para histórico e listas (ex.: "Brigadeiro ×50"). */
    public String describe() {
        return nameSnapshot + " ×" + quantity + (notes == null ? "" : " (" + notes + ")");
    }
}
