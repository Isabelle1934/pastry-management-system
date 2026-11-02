package br.com.confeitaria.modules.catalog.entity;

import br.com.confeitaria.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/** Base de massas e recheios: nome, descrição, ativo e ordem de exibição. */
@MappedSuperclass
public abstract class CatalogOption extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected CatalogOption() {
    }

    protected CatalogOption(String name, String description, int displayOrder) {
        update(name, description, displayOrder);
    }

    public final void update(String name, String description, int displayOrder) {
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
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
