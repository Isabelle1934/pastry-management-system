package br.com.confeitaria.modules.order.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.exception.ConflictException;
import br.com.confeitaria.common.persistence.BaseEntity;
import br.com.confeitaria.modules.catalog.entity.CakeSize;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

/**
 * Encomenda. Nasce como solicitação ({@link CommercialStatus#PENDING_REVIEW}); a vaga na agenda
 * só é ocupada quando o sinal é confirmado (slot_reservation).
 */
@Entity
@Table(name = "cake_order")
public class CakeOrder extends BaseEntity {

    @Column(name = "tracking_code", nullable = false, length = 16, updatable = false)
    private String trackingCode;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    /** E.164, ex.: +5511999998888. */
    @Column(name = "customer_whatsapp", nullable = false, length = 20)
    private String customerWhatsapp;

    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;

    @Column(name = "pickup_time", nullable = false)
    private LocalTime pickupTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "size_id", nullable = false)
    private CakeSize size;

    @Column(name = "size_name_snapshot", nullable = false, length = 80)
    private String sizeNameSnapshot;

    @ElementCollection
    @CollectionTable(name = "order_flavor", joinColumns = @JoinColumn(name = "order_id"))
    @MapKeyColumn(name = "flavor_id")
    private Map<Long, OptionChoice> flavors = new LinkedHashMap<>();

    @ElementCollection
    @CollectionTable(name = "order_filling", joinColumns = @JoinColumn(name = "order_id"))
    @MapKeyColumn(name = "filling_id")
    private Map<Long, OptionChoice> fillings = new LinkedHashMap<>();

    @Column(length = 1000)
    private String decoration;

    @Column(name = "cake_text", length = 100)
    private String cakeText;

    @Column(length = 1000)
    private String notes;

    @Column(name = "topper_requested", nullable = false)
    private boolean topperRequested;

    @Column(name = "topper_theme", length = 100)
    private String topperTheme;

    @Column(name = "topper_name", length = 60)
    private String topperName;

    @Column(name = "topper_age")
    private Short topperAge;

    @Enumerated(EnumType.STRING)
    @Column(name = "commercial_status", nullable = false, length = 30)
    private CommercialStatus commercialStatus = CommercialStatus.PENDING_REVIEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "production_status", nullable = false, length = 30)
    private ProductionStatus productionStatus = ProductionStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.NONE;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 300)
    private String cancellationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = 20)
    private ActorType cancelledBy;

    protected CakeOrder() {
    }

    public CakeOrder(String trackingCode, String customerName, String customerWhatsapp) {
        this.trackingCode = Objects.requireNonNull(trackingCode);
        this.customerName = Objects.requireNonNull(customerName);
        this.customerWhatsapp = Objects.requireNonNull(customerWhatsapp);
    }

    public void schedule(LocalDate pickupDate, LocalTime pickupTime) {
        this.pickupDate = Objects.requireNonNull(pickupDate);
        this.pickupTime = Objects.requireNonNull(pickupTime);
    }

    public void customize(CakeSize size, String decoration, String cakeText, String notes) {
        this.size = Objects.requireNonNull(size);
        this.sizeNameSnapshot = size.getName();
        this.decoration = decoration;
        this.cakeText = cakeText;
        this.notes = notes;
    }

    /** @param choices id do item → nome, na ordem escolhida pela cliente */
    public void chooseFlavors(Map<Long, String> choices) {
        replace(flavors, choices);
    }

    public void chooseFillings(Map<Long, String> choices) {
        replace(fillings, choices);
    }

    public void requestTopper(String theme, String name, Short age) {
        this.topperRequested = true;
        this.topperTheme = Objects.requireNonNull(theme);
        this.topperName = name;
        this.topperAge = age;
    }

    public void removeTopper() {
        this.topperRequested = false;
        this.topperTheme = null;
        this.topperName = null;
        this.topperAge = null;
    }

    public void updateCustomer(String customerName, String customerWhatsapp) {
        this.customerName = Objects.requireNonNull(customerName);
        this.customerWhatsapp = Objects.requireNonNull(customerWhatsapp);
    }

    /** Transições permitidas da situação comercial (BUSINESS_RULES.md §2). */
    private static final Map<CommercialStatus, Set<CommercialStatus>> TRANSITIONS = Map.of(
            CommercialStatus.PENDING_REVIEW, Set.of(CommercialStatus.QUOTE_SENT, CommercialStatus.REJECTED,
                    CommercialStatus.CANCELLED),
            CommercialStatus.QUOTE_SENT, Set.of(CommercialStatus.PENDING_REVIEW, CommercialStatus.QUOTE_SENT,
                    CommercialStatus.AWAITING_DEPOSIT, CommercialStatus.DECLINED_BY_CLIENT, CommercialStatus.REJECTED,
                    CommercialStatus.CANCELLED),
            CommercialStatus.AWAITING_DEPOSIT, Set.of(CommercialStatus.QUOTE_SENT, CommercialStatus.CONFIRMED,
                    CommercialStatus.CANCELLED),
            CommercialStatus.DECLINED_BY_CLIENT, Set.of(CommercialStatus.QUOTE_SENT, CommercialStatus.CANCELLED),
            CommercialStatus.CONFIRMED, Set.of(CommercialStatus.COMPLETED, CommercialStatus.CANCELLED),
            CommercialStatus.COMPLETED, Set.of(),
            CommercialStatus.REJECTED, Set.of(),
            CommercialStatus.CANCELLED, Set.of());

    public boolean canTransitionTo(CommercialStatus target) {
        return TRANSITIONS.get(commercialStatus).contains(target);
    }

    /** Muda a situação comercial; transições não previstas são recusadas (409). Cancelamento usa {@link #cancel}. */
    public void transitionTo(CommercialStatus target) {
        if (target == CommercialStatus.CANCELLED) {
            throw new IllegalArgumentException("Use cancel() para cancelar o pedido");
        }
        requireTransition(target);
        this.commercialStatus = target;
    }

    public void cancel(ActorType by, String reason, Instant now) {
        requireTransition(CommercialStatus.CANCELLED);
        this.commercialStatus = CommercialStatus.CANCELLED;
        this.cancelledBy = Objects.requireNonNull(by);
        this.cancellationReason = reason;
        this.cancelledAt = Objects.requireNonNull(now);
    }

    private void requireTransition(CommercialStatus target) {
        if (!canTransitionTo(target)) {
            throw new ConflictException("INVALID_STATUS_TRANSITION",
                    "Esta ação não é possível na situação atual do pedido.");
        }
    }

    /** Situação consolidada dos pagamentos — calculada pelo PaymentService a cada mudança. */
    public void updatePaymentStatus(PaymentStatus status) {
        this.paymentStatus = Objects.requireNonNull(status);
    }

    /** Situações finais: nada mais pode ser alterado. */
    public boolean isClosed() {
        return TRANSITIONS.get(commercialStatus).isEmpty();
    }

    /** A cliente só edita antes de aceitar o orçamento (BUSINESS_RULES.md §4). */
    public boolean isEditableByClient() {
        return commercialStatus == CommercialStatus.PENDING_REVIEW || commercialStatus == CommercialStatus.QUOTE_SENT;
    }

    public Map<Long, String> getFlavorChoices() {
        return choiceMap(flavors);
    }

    public Map<Long, String> getFillingChoices() {
        return choiceMap(fillings);
    }

    private static Map<Long, String> choiceMap(Map<Long, OptionChoice> choices) {
        Map<Long, String> result = new LinkedHashMap<>();
        choices.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().getPosition()))
                .forEach(e -> result.put(e.getKey(), e.getValue().getNameSnapshot()));
        return result;
    }

    private static void replace(Map<Long, OptionChoice> target, Map<Long, String> choices) {
        target.clear();
        int position = 0;
        for (Map.Entry<Long, String> choice : choices.entrySet()) {
            target.put(choice.getKey(), new OptionChoice(choice.getValue(), position++));
        }
    }

    private static List<String> namesInOrder(Map<Long, OptionChoice> choices) {
        return choices.values().stream()
                .sorted(Comparator.comparingInt(OptionChoice::getPosition))
                .map(OptionChoice::getNameSnapshot)
                .toList();
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerWhatsapp() {
        return customerWhatsapp;
    }

    public LocalDate getPickupDate() {
        return pickupDate;
    }

    public LocalTime getPickupTime() {
        return pickupTime;
    }

    public CakeSize getSize() {
        return size;
    }

    public String getSizeNameSnapshot() {
        return sizeNameSnapshot;
    }

    public List<String> getFlavorNames() {
        return namesInOrder(flavors);
    }

    public List<String> getFillingNames() {
        return namesInOrder(fillings);
    }

    public String getDecoration() {
        return decoration;
    }

    public String getCakeText() {
        return cakeText;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isTopperRequested() {
        return topperRequested;
    }

    public String getTopperTheme() {
        return topperTheme;
    }

    public String getTopperName() {
        return topperName;
    }

    public Short getTopperAge() {
        return topperAge;
    }

    public CommercialStatus getCommercialStatus() {
        return commercialStatus;
    }

    public ProductionStatus getProductionStatus() {
        return productionStatus;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public ActorType getCancelledBy() {
        return cancelledBy;
    }
}
