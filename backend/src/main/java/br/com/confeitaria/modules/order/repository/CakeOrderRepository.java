package br.com.confeitaria.modules.order.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.confeitaria.modules.order.entity.CakeOrder;

public interface CakeOrderRepository extends JpaRepository<CakeOrder, Long> {

    Optional<CakeOrder> findByTrackingCode(String trackingCode);

    boolean existsByTrackingCode(String trackingCode);
}
