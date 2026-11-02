package br.com.confeitaria.modules.catalog.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Recheio do bolo. */
@Entity
@Table(name = "cake_filling")
public class CakeFilling extends CatalogOption {

    protected CakeFilling() {
    }

    public CakeFilling(String name, String description, int displayOrder) {
        super(name, description, displayOrder);
    }
}
