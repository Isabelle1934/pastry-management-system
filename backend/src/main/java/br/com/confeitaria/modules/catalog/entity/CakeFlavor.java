package br.com.confeitaria.modules.catalog.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Massa do bolo. */
@Entity
@Table(name = "cake_flavor")
public class CakeFlavor extends CatalogOption {

    protected CakeFlavor() {
    }

    public CakeFlavor(String name, String description, int displayOrder) {
        super(name, description, displayOrder);
    }
}
