package br.com.confeitaria.modules.catalog.entity;

import br.com.confeitaria.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Categoria do catálogo (ex.: Bolos, Brigadeiros, Kits), criada pela proprietária. */
@Entity
@Table(name = "product_category")
public class ProductCategory extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected ProductCategory() {
    }

    public ProductCategory(String name, String description, int displayOrder) {
        update(name, description, displayOrder);
    }

    public final void update(String name, String description, int displayOrder) {
        this.name = name.strip();
        this.description = description == null || description.isBlank() ? null : description.strip();
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

    public boolean isActive() {
        return active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
