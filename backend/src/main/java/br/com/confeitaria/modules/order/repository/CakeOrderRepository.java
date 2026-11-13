package br.com.confeitaria.modules.order.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.order.entity.CommercialStatus;
import jakarta.persistence.LockModeType;

public interface CakeOrderRepository extends JpaRepository<CakeOrder, Long>, JpaSpecificationExecutor<CakeOrder> {

    Optional<CakeOrder> findByTrackingCode(String trackingCode);

    /** Trava o pedido durante mudanças de situação (evita, por ex., aceite e cancelamento simultâneos). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CakeOrder o where o.id = :id")
    Optional<CakeOrder> findByIdForUpdate(Long id);

    @Query("select o.commercialStatus as status, count(o) as total from CakeOrder o group by o.commercialStatus")
    List<StatusCount> countByStatus();

    interface StatusCount {
        CommercialStatus getStatus();

        long getTotal();
    }

    boolean existsByTrackingCode(String trackingCode);

    /** Solicitações recentes da mesma cliente para a mesma data/horário (detecção de envio repetido). */
    @Query("""
            select o from CakeOrder o
            where o.customerWhatsapp = :whatsapp and o.pickupDate = :date and o.pickupTime = :time
              and o.createdAt >= :since
              and o.commercialStatus not in (br.com.confeitaria.modules.order.entity.CommercialStatus.CANCELLED,
                  br.com.confeitaria.modules.order.entity.CommercialStatus.REJECTED,
                  br.com.confeitaria.modules.order.entity.CommercialStatus.DECLINED_BY_CLIENT)
            """)
    List<CakeOrder> findRecentSameSchedule(String whatsapp, java.time.LocalDate date, java.time.LocalTime time,
            java.time.Instant since);

    /** Lista de retiradas: pedidos na situação informada com data até {@code until} (inclui atrasados). */
    List<CakeOrder> findByCommercialStatusAndPickupDateLessThanEqualOrderByPickupDateAscPickupTimeAscIdAsc(
            CommercialStatus status, LocalDate until);
}
