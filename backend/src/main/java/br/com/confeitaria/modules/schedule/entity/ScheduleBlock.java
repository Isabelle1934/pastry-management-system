package br.com.confeitaria.modules.schedule.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bloqueio de uma data inteira ({@code startTime == null}) ou de um horário específico. */
@Entity
@Table(name = "schedule_block")
public class ScheduleBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_date", nullable = false, updatable = false)
    private LocalDate blockDate;

    @Column(name = "start_time", updatable = false)
    private LocalTime startTime;

    @Column(length = 200)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ScheduleBlock() {
    }

    public ScheduleBlock(LocalDate blockDate, LocalTime startTime, String reason, Instant createdAt) {
        this.blockDate = blockDate;
        this.startTime = startTime;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public boolean isWholeDay() {
        return startTime == null;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getBlockDate() {
        return blockDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
