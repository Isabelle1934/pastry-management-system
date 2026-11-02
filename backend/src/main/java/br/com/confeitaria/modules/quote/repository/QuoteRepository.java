package br.com.confeitaria.modules.quote.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import br.com.confeitaria.modules.quote.entity.Quote;
import br.com.confeitaria.modules.quote.entity.QuoteStatus;
import jakarta.persistence.LockModeType;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    List<Quote> findByOrderIdOrderByVersionNumberDesc(Long orderId);

    @Query("select coalesce(max(q.versionNumber), 0) from Quote q where q.orderId = :orderId")
    int maxVersionNumber(Long orderId);

    @Query("select q from Quote q where q.orderId = :orderId and q.status in ('SENT', 'ACCEPTED')")
    Optional<Quote> findCurrent(Long orderId);

    @Query("select q from Quote q where q.orderId = :orderId and q.status = 'DRAFT'")
    Optional<Quote> findDraft(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quote q where q.id = :id")
    Optional<Quote> findByIdForUpdate(Long id);

    List<Quote> findByOrderIdInAndStatusIn(Collection<Long> orderIds, Collection<QuoteStatus> statuses);

    /** Orçamento vigente (enviado/aceito) de cada pedido da lista. */
    default Map<Long, Quote> currentByOrder(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return findByOrderIdInAndStatusIn(orderIds, List.of(QuoteStatus.SENT, QuoteStatus.ACCEPTED)).stream()
                .collect(Collectors.toMap(Quote::getOrderId, q -> q));
    }
}
