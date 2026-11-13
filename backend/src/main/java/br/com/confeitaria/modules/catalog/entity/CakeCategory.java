package br.com.confeitaria.modules.catalog.entity;

import br.com.confeitaria.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Categoria de bolo personalizado (ex.: baixos, altos, 2 andares). Agrupa os tamanhos e mostra as
 * características e avisos da categoria. Uma categoria "consulte valores" não tem tabela de preço:
 * a cliente descreve o bolo e o valor vem no orçamento.
 */
@Entity
@Table(name = "cake_category")
public class CakeCategory extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(length = 300)
    private String notice;

    @Column(name = "consult_only", nullable = false)
    private boolean consultOnly;

    /** Limites usados quando não há tamanho (categoria "consulte valores"). */
    @Column(name = "max_flavors", nullable = false)
    private int maxFlavors = 1;

    @Column(name = "max_fillings", nullable = false)
    private int maxFillings = 2;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected CakeCategory() {
    }

    public CakeCategory(String name, String description, String notice, boolean consultOnly, int maxFlavors,
            int maxFillings, int displayOrder) {
        update(name, description, notice, consultOnly, maxFlavors, maxFillings, displayOrder);
    }

    public final void update(String name, String description, String notice, boolean consultOnly, int maxFlavors,
            int maxFillings, int displayOrder) {
        this.name = name.strip();
        this.description = blankToNull(description);
        this.notice = blankToNull(notice);
        this.consultOnly = consultOnly;
        this.maxFlavors = maxFlavors;
        this.maxFillings = maxFillings;
        this.displayOrder = displayOrder;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
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

    public String getNotice() {
        return notice;
    }

    public boolean isConsultOnly() {
        return consultOnly;
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
