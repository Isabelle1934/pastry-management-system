package br.com.confeitaria.modules.order.repository;

import java.util.List;

import org.springframework.data.repository.Repository;

import br.com.confeitaria.modules.order.entity.OrderHistory;

/** Somente inserção e leitura. */
public interface OrderHistoryRepository extends Repository<OrderHistory, Long> {

    OrderHistory save(OrderHistory history);

    List<OrderHistory> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);
}
