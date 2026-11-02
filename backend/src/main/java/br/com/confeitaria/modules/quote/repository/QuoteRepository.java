package br.com.confeitaria.modules.quote.repository;

import java.time.Instant;
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

    /** Orçamentos enviados cujo prazo já terminou (processo agendado). */
    @Query("select q.id from Quote q where q.status = 'SENT' and q.validUntil <= :now order by q.validUntil")
    List<Long> findExpiredSentIds(Instant now);

    /** Orçamentos enviados que vencem até {@code limit} (aviso antecipado). */
    @Query("select q from Quote q where q.status = 'SENT' and q.validUntil > :now and q.validUntil <= :limit")
    List<Quote> findSentExpiringBetween(Instant now, Instant limit);

    /** Orçamentos aceitos há mais tempo que {@code respondedBefore}, de pedidos ainda aguardando o sinal. */
    @Query("""
            select q from Quote q, CakeOrder o
            where o.id = q.orderId and q.status = 'ACCEPTED'
              and o.commercialStatus = br.com.confeitaria.modules.order.entity.CommercialStatus.AWAITING_DEPOSIT
              and q.respondedAt <= :respondedBefore
            order by q.respondedAt
            """)
    List<Quote> findAcceptedAwaitingDepositSince(Instant respondedBefore);

    /** Orçamento vigente (enviado/aceito) de cada pedido da lista. */
    default Map<Long, Quote> currentByOrder(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return findByOrderIdInAndStatusIn(orderIds, List.of(QuoteStatus.SENT, QuoteStatus.ACCEPTED)).stream()
                .collect(Collectors.toMap(Quote::getOrderId, q -> q));
    }
}
