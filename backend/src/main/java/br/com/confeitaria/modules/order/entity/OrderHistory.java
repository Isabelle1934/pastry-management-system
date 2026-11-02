package br.com.confeitaria.modules.order.entity;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.confeitaria.common.audit.ActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Evento do histórico do pedido. Somente inserção: o banco rejeita UPDATE e DELETE. */
@Entity
@Immutable
@Table(name = "order_history")
public class OrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActorType actor;

    @Column(nullable = false, length = 300)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderHistory() {
    }

    public OrderHistory(Long orderId, String eventType, ActorType actor, String description, Map<String, Object> details,
            Instant createdAt) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.actor = actor;
        this.description = description;
        this.details = details == null || details.isEmpty() ? null : Map.copyOf(details);
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getEventType() {
        return eventType;
    }

    public ActorType getActor() {
        return actor;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
