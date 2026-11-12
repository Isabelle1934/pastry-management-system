package br.com.confeitaria.modules.catalog.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import br.com.confeitaria.common.persistence.BaseEntity;
import br.com.confeitaria.modules.order.entity.FulfillmentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Produto do catálogo. Não é apagado: sai do catálogo público ao ficar inativo. Os pedidos guardam
 * uma cópia de nome, categoria e preço, então editar o produto não altera pedidos já registrados.
 */
@Entity
@Table(name = "product")
public class Product extends BaseEntity {

    /** Dados editáveis, já validados pelo serviço. */
    public record Data(ProductCategory category, String name, String description, BigDecimal price, String unitLabel,
            int minQuantity, boolean orderable, boolean seasonal, LocalDate availableFrom, LocalDate availableUntil,
            Set<DayOfWeek> availableDays, boolean pickupAllowed, boolean deliveryAllowed, int displayOrder) {
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategory category;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    /** {@code null} = preço sob orçamento. */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "unit_label", length = 40)
    private String unitLabel;

    @Column(name = "min_quantity", nullable = false)
    private int minQuantity = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ACTIVE;

    /** Aceita encomendas pelo site (falso = só vitrine). */
    @Column(nullable = false)
    private boolean orderable = true;

    /** Produto especial ou temporário (destacado no catálogo). */
    @Column(nullable = false)
    private boolean seasonal;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "available_until")
    private LocalDate availableUntil;

    /** Máscara ISO: bit 0 = segunda ... bit 6 = domingo; {@code null} = todos os dias. */
    @Column(name = "available_weekdays")
    private Short availableWeekdays;

    @Column(name = "pickup_allowed", nullable = false)
    private boolean pickupAllowed = true;

    @Column(name = "delivery_allowed", nullable = false)
    private boolean deliveryAllowed;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected Product() {
    }

    public Product(Data data) {
        update(data);
    }

    public final void update(Data data) {
        this.category = Objects.requireNonNull(data.category());
        this.name = data.name().strip();
        this.description = blankToNull(data.description());
        this.price = data.price() == null ? null : data.price().setScale(2, RoundingMode.HALF_UP);
        this.unitLabel = blankToNull(data.unitLabel());
        this.minQuantity = data.minQuantity();
        this.orderable = data.orderable();
        this.seasonal = data.seasonal();
        this.availableFrom = data.availableFrom();
        this.availableUntil = data.availableUntil();
        this.availableWeekdays = toMask(data.availableDays());
        this.pickupAllowed = data.pickupAllowed();
        this.deliveryAllowed = data.deliveryAllowed();
        this.displayOrder = data.displayOrder();
    }

    public void changeStatus(ProductStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    /** Aparece no catálogo público (ativo ou temporariamente indisponível, em categoria ativa e no período). */
    public boolean isPubliclyVisible(LocalDate today) {
        return status != ProductStatus.INACTIVE && category.isActive()
                && (availableUntil == null || !availableUntil.isBefore(today));
    }

    /** Pode ser escolhido pela cliente (sem considerar a data do pedido). */
    public boolean isSelectable() {
        return status == ProductStatus.ACTIVE && orderable && category.isActive();
    }

    /** Aceita a data agendada: dentro do período (se houver) e em um dos dias da semana permitidos. */
    public boolean acceptsDate(LocalDate date) {
        if (availableFrom != null && date.isBefore(availableFrom)) {
            return false;
        }
        if (availableUntil != null && date.isAfter(availableUntil)) {
            return false;
        }
        return availableWeekdays == null || (availableWeekdays & bit(date.getDayOfWeek())) != 0;
    }

    public boolean allows(FulfillmentType type) {
        return type == FulfillmentType.DELIVERY ? deliveryAllowed : pickupAllowed;
    }

    /** Dias da semana permitidos; vazio = todos. */
    public Set<DayOfWeek> getAvailableDays() {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (availableWeekdays != null) {
            for (DayOfWeek day : DayOfWeek.values()) {
                if ((availableWeekdays & bit(day)) != 0) {
                    days.add(day);
                }
            }
        }
        return days;
    }

    private static int bit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }

    /** Conjunto vazio ou com os 7 dias = sem restrição. */
    private static Short toMask(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty() || days.size() == 7) {
            return null;
        }
        int mask = 0;
        for (DayOfWeek day : days) {
            mask |= bit(day);
        }
        return (short) mask;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public ProductCategory getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getUnitLabel() {
        return unitLabel;
    }

    public int getMinQuantity() {
        return minQuantity;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public boolean isOrderable() {
        return orderable;
    }

    public boolean isSeasonal() {
        return seasonal;
    }

    public LocalDate getAvailableFrom() {
        return availableFrom;
    }

    public LocalDate getAvailableUntil() {
        return availableUntil;
    }

    public boolean isPickupAllowed() {
        return pickupAllowed;
    }

    public boolean isDeliveryAllowed() {
        return deliveryAllowed;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
