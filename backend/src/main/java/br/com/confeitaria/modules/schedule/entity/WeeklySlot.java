package br.com.confeitaria.modules.schedule.entity;

import java.time.DayOfWeek;
import java.time.LocalTime;

import br.com.confeitaria.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Horário de retirada do modelo semanal (ex.: toda sexta às 14:00, até 3 pedidos). */
@Entity
@Table(name = "weekly_slot")
public class WeeklySlot extends BaseEntity {

    /** ISO-8601: 1 = segunda ... 7 = domingo. */
    @Column(name = "day_of_week", nullable = false, updatable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false, updatable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean active = true;

    protected WeeklySlot() {
    }

    public WeeklySlot(DayOfWeek dayOfWeek, LocalTime startTime, int capacity) {
        this.dayOfWeek = (short) dayOfWeek.getValue();
        this.startTime = startTime.withSecond(0).withNano(0);
        this.capacity = capacity;
    }

    public void update(int capacity, boolean active) {
        this.capacity = capacity;
        this.active = active;
    }

    public DayOfWeek getDayOfWeek() {
        return DayOfWeek.of(dayOfWeek);
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isActive() {
        return active;
    }
}
