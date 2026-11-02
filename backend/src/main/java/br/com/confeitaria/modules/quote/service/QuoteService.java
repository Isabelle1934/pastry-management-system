package br.com.confeitaria.modules.quote.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.exception.ConflictException;
import br.com.confeitaria.common.exception.NotFoundException;
import br.com.confeitaria.modules.notification.entity.NotificationType;
import br.com.confeitaria.modules.notification.service.NotificationService;
import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.order.entity.CommercialStatus;
import br.com.confeitaria.modules.order.repository.CakeOrderRepository;
import br.com.confeitaria.modules.order.service.OrderHistoryService;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.DraftRequest;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.QuoteView;
import br.com.confeitaria.modules.quote.entity.Quote;
import br.com.confeitaria.modules.quote.entity.QuoteStatus;
import br.com.confeitaria.modules.quote.repository.QuoteRepository;
import br.com.confeitaria.modules.settings.service.SettingsService;

/**
 * Orçamentos: a proprietária monta um rascunho e o envia; cada envio é uma nova versão e a anterior
 * fica registrada como substituída. A cliente aceita ou recusa a versão vigente.
 */
@Service
public class QuoteService {

    public static final String QUOTE_EXPIRED = "QUOTE_EXPIRED";

    /** Situações do pedido em que a proprietária pode preparar e enviar orçamento. */
    private static final Set<CommercialStatus> QUOTABLE = Set.of(CommercialStatus.PENDING_REVIEW,
            CommercialStatus.QUOTE_SENT, CommercialStatus.AWAITING_DEPOSIT, CommercialStatus.DECLINED_BY_CLIENT);

    private final QuoteRepository quotes;
    private final CakeOrderRepository orders;
    private final OrderHistoryService history;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final Clock clock;

    public QuoteService(QuoteRepository quotes, CakeOrderRepository orders, OrderHistoryService history,
            SettingsService settings, NotificationService notifications, Clock clock) {
        this.quotes = quotes;
        this.orders = orders;
        this.history = history;
        this.settings = settings;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<QuoteView> list(Long orderId) {
        return quotes.findByOrderIdOrderByVersionNumberDesc(orderId).stream().map(QuoteView::from).toList();
    }

    /** Cria ou atualiza o rascunho do pedido (só existe um rascunho por vez). */
    @Transactional
    public QuoteView saveDraft(Long orderId, DraftRequest request) {
        CakeOrder order = lockOrder(orderId);
        requireQuotable(order);
        Quote draft = quotes.findDraft(orderId)
                .orElseGet(() -> new Quote(orderId, quotes.maxVersionNumber(orderId) + 1));
        int validity = request.validityHours() != null ? request.validityHours()
                : settings.current().getQuoteValidityHours();
        draft.editDraft(request.items().stream()
                .map(i -> new Quote.ItemData(i.name().strip(), blankToNull(i.description()), i.quantity(), i.unitPrice()))
                .toList(), blankToNull(request.notes()), validity);
        return QuoteView.from(quotes.saveAndFlush(draft));
    }

    @Transactional
    public void deleteDraft(Long orderId) {
        lockOrder(orderId);
        Quote draft = quotes.findDraft(orderId).orElseThrow(() -> new NotFoundException("Não há rascunho de orçamento."));
        quotes.delete(draft);
    }

    /**
     * Envia o rascunho. A versão vigente anterior (enviada ou aceita) passa a "substituída"; os pagamentos
     * já registrados não são afetados (eles pertencem ao pedido, não ao orçamento).
     */
    @Transactional
    public QuoteView send(Long orderId, Long actorId) {
        CakeOrder order = lockOrder(orderId);
        requireQuotable(order);
        Quote draft = quotes.findDraft(orderId)
                .orElseThrow(() -> new ConflictException("QUOTE_EMPTY", "Monte o orçamento antes de enviar."));

        quotes.findCurrent(orderId).ifPresent(current -> {
            current.supersede();
            // Grava antes do envio: o índice único permite só um orçamento vigente por pedido.
            quotes.saveAndFlush(current);
        });
        Instant now = clock.instant();
        draft.send(now);
        order.transitionTo(CommercialStatus.QUOTE_SENT);
        quotes.saveAndFlush(draft);

        history.record(orderId, "QUOTE_SENT", ActorType.OWNER,
                "Orçamento versão " + draft.getVersionNumber() + " enviado.",
                Map.of("version", draft.getVersionNumber(), "total", draft.getTotal().toPlainString(),
                        "validUntil", draft.getValidUntil().toString(), "actorId", actorId));
        return QuoteView.from(draft);
    }

    /** Aceite pela cliente. Precisa ser a versão vigente e estar dentro da validade. */
    @Transactional
    public QuoteView accept(Long orderId, Long quoteId) {
        CakeOrder order = lockOrder(orderId);
        Quote quote = quoteOf(orderId, quoteId);
        requireAnswerable(quote);
        quote.accept(clock.instant());
        order.transitionTo(CommercialStatus.AWAITING_DEPOSIT);
        quotes.saveAndFlush(quote);
        history.record(orderId, "QUOTE_ACCEPTED", ActorType.CLIENT,
                "Orçamento versão " + quote.getVersionNumber() + " aceito pela cliente.",
                Map.of("version", quote.getVersionNumber(), "total", quote.getTotal().toPlainString()));
        notifications.notify(NotificationType.QUOTE_ACCEPTED, orderId,
                order.getCustomerName().split(" ")[0] + " aceitou o orçamento de R$ " + quote.getTotal().toPlainString()
                        + ". Aguardando o sinal de R$ " + quote.getDepositAmount().toPlainString() + ".",
                "QUOTE_ACCEPTED:" + quote.getId());
        return QuoteView.from(quote);
    }

    @Transactional
    public QuoteView decline(Long orderId, Long quoteId, String reason) {
        CakeOrder order = lockOrder(orderId);
        Quote quote = quoteOf(orderId, quoteId);
        requireAnswerable(quote);
        quote.decline(blankToNull(reason), clock.instant());
        order.transitionTo(CommercialStatus.DECLINED_BY_CLIENT);
        quotes.saveAndFlush(quote);
        history.record(orderId, "QUOTE_DECLINED", ActorType.CLIENT,
                "Orçamento versão " + quote.getVersionNumber() + " recusado pela cliente.",
                reason == null || reason.isBlank() ? null : Map.of("reason", reason.strip()));
        notifications.notify(NotificationType.QUOTE_DECLINED, orderId,
                order.getCustomerName().split(" ")[0] + " recusou o orçamento"
                        + (reason == null || reason.isBlank() ? "." : ": " + reason.strip()),
                "QUOTE_DECLINED:" + quote.getId());
        return QuoteView.from(quote);
    }

    /**
     * Invalida o orçamento enviado (sem resposta) — usado quando a cliente edita o pedido ou quando
     * a proprietária recusa/cancela. Orçamentos aceitos são mantidos como estão.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean supersedePendingQuote(Long orderId) {
        return quotes.findCurrent(orderId)
                .filter(q -> q.getStatus() == QuoteStatus.SENT)
                .map(q -> {
                    q.supersede();
                    quotes.saveAndFlush(q);
                    return true;
                })
                .orElse(false);
    }

    private void requireAnswerable(Quote quote) {
        if (quote.getStatus() != QuoteStatus.SENT) {
            throw new ConflictException("INVALID_QUOTE_STATUS", "Este orçamento não está mais aguardando resposta.");
        }
        if (quote.isExpiredAt(clock.instant())) {
            throw new ConflictException(QUOTE_EXPIRED,
                    "O prazo deste orçamento terminou. Fale com a confeitaria para receber uma nova proposta.");
        }
    }

    private Quote quoteOf(Long orderId, Long quoteId) {
        return quotes.findByIdForUpdate(quoteId)
                .filter(q -> q.getOrderId().equals(orderId))
                .orElseThrow(() -> new NotFoundException("Orçamento não encontrado."));
    }

    private CakeOrder lockOrder(Long orderId) {
        return orders.findByIdForUpdate(orderId).orElseThrow(() -> new NotFoundException("Pedido não encontrado."));
    }

    private static void requireQuotable(CakeOrder order) {
        if (!QUOTABLE.contains(order.getCommercialStatus())) {
            throw new ConflictException("INVALID_STATUS_TRANSITION",
                    "Não é possível enviar orçamento na situação atual do pedido.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
