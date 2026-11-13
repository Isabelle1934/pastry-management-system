package br.com.confeitaria.modules.catalog.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

import br.com.confeitaria.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tamanho do bolo. O preço-base é só referência ("a partir de"): o valor final
 * de cada encomenda é definido no orçamento.
 */
@Entity
@Table(name = "cake_size")
public class CakeSize extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 300)
    private String description;

    private Integer servings;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "max_flavors", nullable = false)
    private int maxFlavors;

    @Column(name = "max_fillings", nullable = false)
    private int maxFillings;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** Categoria (baixo, alto, 2 andares...). Opcional: tamanhos antigos podem não ter. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CakeCategory category;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "diameter_cm")
    private Integer diameterCm;

    protected CakeSize() {
    }

    public void classify(CakeCategory category, BigDecimal weightKg, Integer diameterCm) {
        this.category = category;
        this.weightKg = weightKg == null ? null : weightKg.setScale(2, RoundingMode.HALF_UP);
        this.diameterCm = diameterCm;
    }

    public CakeCategory getCategory() {
        return category;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public Integer getDiameterCm() {
        return diameterCm;
    }

    /** Visível no site: ativo e, se tiver categoria, com a categoria ativa. */
    public boolean isPubliclyVisible() {
        return active && (category == null || category.isActive());
    }

    public CakeSize(String name, String description, Integer servings, BigDecimal basePrice, int maxFlavors,
            int maxFillings, int displayOrder) {
        update(name, description, servings, basePrice, maxFlavors, maxFillings, displayOrder);
    }

    public final void update(String name, String description, Integer servings, BigDecimal basePrice, int maxFlavors,
            int maxFillings, int displayOrder) {
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.servings = servings;
        this.basePrice = basePrice.setScale(2, RoundingMode.HALF_UP);
        this.maxFlavors = maxFlavors;
        this.maxFillings = maxFillings;
        this.displayOrder = displayOrder;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Integer getServings() {
        return servings;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public int getMaxFlavors() {
        return maxFlavors;
    }

    public int getMaxFillings() {
        return maxFillings;
    }

    public boolean isActive() {
        return active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
