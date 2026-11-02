package br.com.confeitaria.modules.schedule.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.exception.BusinessRuleException;
import br.com.confeitaria.modules.schedule.entity.SlotReservation;
import br.com.confeitaria.modules.schedule.entity.WeeklySlot;
import br.com.confeitaria.modules.schedule.repository.ScheduleBlockRepository;
import br.com.confeitaria.modules.schedule.repository.SlotOccupancyRepository;
import br.com.confeitaria.modules.schedule.repository.SlotReservationRepository;
import br.com.confeitaria.modules.schedule.repository.WeeklySlotRepository;

/**
 * Ocupa e libera vagas de horário. Chamado dentro da transação de quem confirma o sinal,
 * cancela ou remarca um pedido: se a operação principal falhar, a reserva também é desfeita.
 *
 * <p>A garantia de não ultrapassar o limite está no banco (UPDATE condicional em
 * {@code slot_occupancy}) e não em verificações prévias — ver ADR-004.
 */
@Service
public class ReservationService {

    public static final String SLOT_FULL = "SLOT_FULL";
    public static final String SLOT_UNAVAILABLE = "SLOT_UNAVAILABLE";

    private final SlotReservationRepository reservations;
    private final SlotOccupancyRepository occupancy;
    private final WeeklySlotRepository slots;
    private final ScheduleBlockRepository blocks;
    private final Clock clock;

    public ReservationService(SlotReservationRepository reservations, SlotOccupancyRepository occupancy,
            WeeklySlotRepository slots, ScheduleBlockRepository blocks, Clock clock) {
        this.reservations = reservations;
        this.occupancy = occupancy;
        this.slots = slots;
        this.blocks = blocks;
        this.clock = clock;
    }

    /**
     * Ocupa uma vaga no horário para o pedido. Idempotente: se o pedido já ocupa esse horário, nada muda.
     * Se ocupava outro horário (remarcação), a vaga antiga é liberada na mesma transação.
     *
     * @throws BusinessRuleException {@code SLOT_FULL} se não houver vaga; {@code SLOT_UNAVAILABLE} se o horário
     *                               não existir no modelo semanal, estiver inativo ou bloqueado
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SlotReservation reserve(Long orderId, LocalDate date, LocalTime time) {
        Optional<SlotReservation> existing = reservations.findActiveByOrderIdForUpdate(orderId);
        if (existing.isPresent() && existing.get().isFor(date, time)) {
            return existing.get();
        }

        WeeklySlot slot = slots.findByDayOfWeekAndStartTime((short) date.getDayOfWeek().getValue(), time)
                .filter(WeeklySlot::isActive)
                .orElseThrow(ReservationService::unavailable);
        if (blocks.existsByBlockDateAndStartTimeIsNull(date) || blocks.existsByBlockDateAndStartTime(date, time)) {
            throw unavailable();
        }

        existing.ifPresent(previous -> releaseReservation(previous, "Remarcado para " + date + " " + time));

        if (!occupancy.tryIncrement(date, time, slot.getCapacity())) {
            throw new BusinessRuleException(SLOT_FULL,
                    "Este horário já atingiu o limite de pedidos confirmados. Escolha outro horário para o pedido.");
        }
        // saveAndFlush: o índice único de reserva ativa por pedido é verificado já aqui, dentro da transação.
        return reservations.saveAndFlush(new SlotReservation(orderId, date, time, clock.instant()));
    }

    /**
     * Libera a vaga do pedido, se houver. Idempotente.
     *
     * @return {@code true} se havia uma reserva ativa
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean release(Long orderId, String reason) {
        return reservations.findActiveByOrderIdForUpdate(orderId)
                .map(reservation -> {
                    releaseReservation(reservation, reason);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<SlotReservation> activeReservation(Long orderId) {
        return reservations.findActiveByOrderId(orderId);
    }

    private void releaseReservation(SlotReservation reservation, String reason) {
        reservation.release(reason, clock.instant());
        reservations.saveAndFlush(reservation);
        occupancy.decrement(reservation.getSlotDate(), reservation.getSlotTime());
    }

    private static BusinessRuleException unavailable() {
        return new BusinessRuleException(SLOT_UNAVAILABLE,
                "Este horário não está disponível na agenda (inexistente, inativo ou bloqueado).");
    }
}
