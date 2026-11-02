package br.com.confeitaria.modules.schedule.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import br.com.confeitaria.modules.schedule.entity.SlotReservation;
import jakarta.persistence.LockModeType;

public interface SlotReservationRepository extends JpaRepository<SlotReservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SlotReservation r where r.orderId = :orderId and r.status = 'ACTIVE'")
    Optional<SlotReservation> findActiveByOrderIdForUpdate(Long orderId);

    @Query("select r from SlotReservation r where r.orderId = :orderId and r.status = 'ACTIVE'")
    Optional<SlotReservation> findActiveByOrderId(Long orderId);
}
