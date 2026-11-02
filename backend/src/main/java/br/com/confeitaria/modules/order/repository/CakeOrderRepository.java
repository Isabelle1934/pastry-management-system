package br.com.confeitaria.modules.order.repository;

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
}
