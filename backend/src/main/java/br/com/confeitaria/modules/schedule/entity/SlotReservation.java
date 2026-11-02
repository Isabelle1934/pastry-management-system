package br.com.confeitaria.modules.schedule.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Vaga ocupada por um pedido com sinal confirmado. Reservas liberadas ficam como histórico. */
@Entity
@Table(name = "slot_reservation")
public class SlotReservation {

    public enum Status {
        ACTIVE,
        RELEASED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "slot_date", nullable = false, updatable = false)
    private LocalDate slotDate;

    @Column(name = "slot_time", nullable = false, updatable = false)
    private LocalTime slotTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "release_reason", length = 200)
    private String releaseReason;

    protected SlotReservation() {
    }

    public SlotReservation(Long orderId, LocalDate slotDate, LocalTime slotTime, Instant createdAt) {
        this.orderId = orderId;
        this.slotDate = slotDate;
        this.slotTime = slotTime;
        this.createdAt = createdAt;
    }

    public void release(String reason, Instant now) {
        this.status = Status.RELEASED;
        this.releaseReason = reason;
        this.releasedAt = now;
    }

    public boolean isFor(LocalDate date, LocalTime time) {
        return slotDate.equals(date) && slotTime.equals(time);
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public LocalTime getSlotTime() {
        return slotTime;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public String getReleaseReason() {
        return releaseReason;
    }
}
