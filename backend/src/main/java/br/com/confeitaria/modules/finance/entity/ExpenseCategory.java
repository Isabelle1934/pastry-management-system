package br.com.confeitaria.modules.finance.entity;

import br.com.confeitaria.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Categoria de despesa. Não é apagada: é desativada, pois pode estar em despesas antigas. */
@Entity
@Table(name = "expense_category")
public class ExpenseCategory extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    protected ExpenseCategory() {
    }

    public ExpenseCategory(String name) {
        rename(name);
    }

    public void rename(String name) {
        this.name = name.strip();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
