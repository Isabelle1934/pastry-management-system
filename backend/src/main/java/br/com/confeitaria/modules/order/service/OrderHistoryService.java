package br.com.confeitaria.modules.order.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.modules.order.entity.OrderHistory;
import br.com.confeitaria.modules.order.repository.OrderHistoryRepository;

/** Registra eventos do pedido junto com a operação que os causou (mesma transação). */
@Service
public class OrderHistoryService {

    private final OrderHistoryRepository repository;
    private final Clock clock;

    public OrderHistoryService(OrderHistoryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OrderHistory record(Long orderId, String eventType, ActorType actor, String description,
            Map<String, Object> details) {
        return repository.save(new OrderHistory(orderId, eventType, actor, description, details, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<OrderHistory> historyOf(Long orderId) {
        return repository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId);
    }
}
