package br.com.confeitaria.modules.deadline;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.text.Texts;
import br.com.confeitaria.modules.notification.entity.NotificationType;
import br.com.confeitaria.modules.notification.service.NotificationMailer;
import br.com.confeitaria.modules.notification.service.NotificationService;
import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.order.entity.CommercialStatus;
import br.com.confeitaria.modules.order.repository.CakeOrderRepository;
import br.com.confeitaria.modules.order.service.OrderHistoryService;
import br.com.confeitaria.modules.payment.service.PaymentService;
import br.com.confeitaria.modules.quote.entity.Quote;
import br.com.confeitaria.modules.quote.entity.QuoteStatus;
import br.com.confeitaria.modules.quote.repository.QuoteRepository;
import br.com.confeitaria.modules.schedule.service.ReservationService;
import br.com.confeitaria.modules.settings.entity.BakerySettings;
import br.com.confeitaria.modules.settings.service.SettingsService;

/**
 * Prazos (BUSINESS_RULES.md §7): avisos antecipados, vencimento de orçamentos sem resposta e prazo
 * para pagar o sinal. Executado pelo {@link DeadlineScheduler} — no servidor, mesmo com o painel fechado.
 *
 * <p>Idempotente: cada item é reavaliado dentro da sua própria transação, com o pedido travado,
 * e só muda se ainda estiver na situação esperada; os avisos têm chave única. Rodar duas vezes
 * produz o mesmo resultado que rodar uma vez. A falha de um item não impede os demais.
 */
@Service
public class DeadlineService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineService.class);

    public record RunResult(int quoteWarnings, int quotesExpired, int depositWarnings, int depositDeadlines,
            int emailsRetried) {
    }

    private final QuoteRepository quotes;
    private final CakeOrderRepository orders;
    private final PaymentService payments;
    private final ReservationService reservations;
    private final OrderHistoryService history;
    private final NotificationService notifications;
    private final NotificationMailer mailer;
    private final SettingsService settings;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DeadlineService(QuoteRepository quotes, CakeOrderRepository orders, PaymentService payments,
            ReservationService reservations, OrderHistoryService history, NotificationService notifications,
            NotificationMailer mailer, SettingsService settings, TransactionTemplate tx, Clock clock) {
        this.quotes = quotes;
        this.orders = orders;
        this.payments = payments;
        this.reservations = reservations;
        this.history = history;
        this.notifications = notifications;
        this.mailer = mailer;
        this.settings = settings;
        this.tx = tx;
        this.clock = clock;
    }

    public RunResult runAll() {
        BakerySettings config = settings.current();
        Instant now = clock.instant();
        int quoteWarnings = warnExpiringQuotes(now, config);
        int expired = expireQuotes(now);
        int depositWarnings = warnDepositDeadlines(now, config);
        int deposits = handleDepositDeadlines(now, config);
        int emails = retryEmails();
        RunResult result = new RunResult(quoteWarnings, expired, depositWarnings, deposits, emails);
        if (quoteWarnings + expired + depositWarnings + deposits + emails > 0) {
            log.info("Prazos processados: {}", result);
        }
        return result;
    }

    // --- Orçamentos ---

    int warnExpiringQuotes(Instant now, BakerySettings config) {
        Instant limit = now.plus(Duration.ofHours(config.getQuoteExpiryWarningHours()));
        return count(quotes.findSentExpiringBetween(now, limit), quote -> notifications.notify(
                NotificationType.QUOTE_EXPIRING, quote.getOrderId(),
                "O orçamento de " + Texts.brl(quote.getTotal()) + " vence em breve sem resposta da cliente. "
                        + "Se não for aceito até o prazo, o pedido será cancelado automaticamente.",
                "QUOTE_EXPIRING:" + quote.getId()).isPresent());
    }

    int expireQuotes(Instant now) {
        return count(quotes.findExpiredSentIds(now), this::expireQuote);
    }

    private boolean expireQuote(Long quoteId) {
        Boolean changed = tx.execute(status -> {
            Long orderId = quotes.findById(quoteId).map(Quote::getOrderId).orElse(null);
            if (orderId == null) {
                return false;
            }
            CakeOrder order = orders.findByIdForUpdate(orderId).orElseThrow();
            Quote quote = quotes.findByIdForUpdate(quoteId).orElseThrow();
            Instant now = clock.instant();
            if (quote.getStatus() != QuoteStatus.SENT || !quote.isExpiredAt(now)) {
                return false;
            }
            quote.expire(now);
            quotes.saveAndFlush(quote);
            String reason = "Orçamento vencido sem resposta da cliente.";
            boolean cancelled = order.getCommercialStatus() == CommercialStatus.QUOTE_SENT;
            if (cancelled) {
                order.cancel(ActorType.SYSTEM, reason, now);
                reservations.release(orderId, reason);
                orders.saveAndFlush(order);
            }
            history.record(orderId, "QUOTE_EXPIRED", ActorType.SYSTEM,
                    cancelled ? reason + " Pedido cancelado automaticamente." : "Orçamento vencido.",
                    Map.of("version", quote.getVersionNumber(), "validUntil", quote.getValidUntil().toString()));
            notifications.notify(NotificationType.QUOTE_EXPIRED, orderId,
                    "O orçamento de " + order.getCustomerName().split(" ")[0] + " venceu sem resposta"
                            + (cancelled ? " e o pedido foi cancelado automaticamente." : "."),
                    "QUOTE_EXPIRED:" + quoteId);
            return true;
        });
        return Boolean.TRUE.equals(changed);
    }

    // --- Prazo do sinal (pendência P4) ---

    int warnDepositDeadlines(Instant now, BakerySettings config) {
        Integer deadline = config.getDepositDeadlineHours();
        if (deadline == null) {
            return 0;
        }
        int warning = config.getQuoteExpiryWarningHours() < deadline ? config.getQuoteExpiryWarningHours() : Math.max(deadline / 2, 1);
        Instant respondedBefore = now.minus(Duration.ofHours(deadline - warning));
        Instant alreadyOverdue = now.minus(Duration.ofHours(deadline));
        return count(quotes.findAcceptedAwaitingDepositSince(respondedBefore), quote -> {
            if (!quote.getRespondedAt().isAfter(alreadyOverdue) || payments.hasProofUnderReview(quote.getOrderId())) {
                return false;
            }
            return notifications.notify(NotificationType.DEPOSIT_DEADLINE_NEAR, quote.getOrderId(),
                    "O prazo para o sinal de " + Texts.brl(quote.getDepositAmount())
                            + " termina em breve e ainda não há pagamento. Sem sinal, o pedido será cancelado automaticamente.",
                    "DEPOSIT_DEADLINE_NEAR:" + quote.getId()).isPresent();
        });
    }

    int handleDepositDeadlines(Instant now, BakerySettings config) {
        Integer deadline = config.getDepositDeadlineHours();
        if (deadline == null) {
            return 0;
        }
        Instant respondedBefore = now.minus(Duration.ofHours(deadline));
        return count(quotes.findAcceptedAwaitingDepositSince(respondedBefore), quote -> handleDepositDeadline(quote.getId()));
    }

    /**
     * Sem nenhum pagamento: cancela e libera a vaga (que normalmente nem foi ocupada). Com comprovante em
     * análise ou valor já recebido, só avisa a proprietária — há dinheiro envolvido e a decisão é dela.
     */
    private boolean handleDepositDeadline(Long quoteId) {
        Boolean changed = tx.execute(status -> {
            Quote quote = quotes.findById(quoteId).orElseThrow();
            CakeOrder order = orders.findByIdForUpdate(quote.getOrderId()).orElseThrow();
            if (order.getCommercialStatus() != CommercialStatus.AWAITING_DEPOSIT) {
                return false;
            }
            String firstName = order.getCustomerName().split(" ")[0];
            if (payments.hasProofUnderReview(order.getId()) || payments.hasConfirmedPayments(order.getId())) {
                return notifications.notify(NotificationType.DEPOSIT_DEADLINE_PASSED, order.getId(),
                        "O prazo do sinal de " + firstName + " terminou, mas há comprovante em análise ou valor recebido. "
                                + "O pedido não foi cancelado: confira os pagamentos.",
                        "DEPOSIT_DEADLINE_PASSED:" + quoteId).isPresent();
            }
            Instant now = clock.instant();
            String reason = "Sinal não pago no prazo.";
            order.cancel(ActorType.SYSTEM, reason, now);
            reservations.release(order.getId(), reason);
            orders.saveAndFlush(order);
            history.record(order.getId(), "DEPOSIT_DEADLINE_PASSED", ActorType.SYSTEM,
                    reason + " Pedido cancelado automaticamente.", Map.of("quoteVersion", quote.getVersionNumber()));
            notifications.notify(NotificationType.DEPOSIT_DEADLINE_PASSED, order.getId(),
                    "O pedido de " + firstName + " foi cancelado automaticamente: o sinal não foi pago no prazo.",
                    "DEPOSIT_DEADLINE_PASSED:" + quoteId);
            return true;
        });
        return Boolean.TRUE.equals(changed);
    }

    // --- E-mails pendentes ---

    int retryEmails() {
        return count(notifications.emailsToRetry(), id -> {
            mailer.send(id);
            return true;
        });
    }

    private <T> int count(List<T> items, Predicate<T> action) {
        int changed = 0;
        for (T item : items) {
            try {
                if (action.test(item)) {
                    changed++;
                }
            } catch (RuntimeException e) {
                log.error("Falha ao processar prazo de {}: {}", item, e.getClass().getSimpleName(), e);
            }
        }
        return changed;
    }
}
