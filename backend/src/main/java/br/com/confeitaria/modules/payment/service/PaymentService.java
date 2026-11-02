package br.com.confeitaria.modules.payment.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.exception.ConflictException;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.common.exception.NotFoundException;
import br.com.confeitaria.modules.file.entity.FilePurpose;
import br.com.confeitaria.modules.file.service.FileStorageService;
import br.com.confeitaria.modules.notification.entity.NotificationType;
import br.com.confeitaria.modules.notification.service.NotificationService;
import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.order.entity.CommercialStatus;
import br.com.confeitaria.modules.order.entity.PaymentStatus;
import br.com.confeitaria.modules.order.repository.CakeOrderRepository;
import br.com.confeitaria.modules.order.service.OrderHistoryService;
import br.com.confeitaria.modules.payment.dto.PaymentDtos.PaymentSummary;
import br.com.confeitaria.modules.payment.dto.PaymentDtos.PaymentView;
import br.com.confeitaria.modules.payment.dto.PaymentDtos.RegisterRequest;
import br.com.confeitaria.modules.payment.entity.Payment;
import br.com.confeitaria.modules.payment.repository.PaymentRepository;
import br.com.confeitaria.modules.quote.entity.Quote;
import br.com.confeitaria.modules.quote.entity.QuoteStatus;
import br.com.confeitaria.modules.quote.repository.QuoteRepository;
import br.com.confeitaria.modules.schedule.service.ReservationService;

/**
 * Pagamentos: comprovante do sinal enviado pela cliente, conferência pela proprietária, registro
 * manual (Pix ou dinheiro, inclusive o saldo na retirada) e reembolsos. Confirmar o sinal confirma
 * o pedido e ocupa a vaga na agenda — tudo na mesma transação (BUSINESS_RULES.md §1, passos 6–10).
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final CakeOrderRepository orders;
    private final QuoteRepository quotes;
    private final ReservationService reservations;
    private final FileStorageService files;
    private final OrderHistoryService history;
    private final NotificationService notifications;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, CakeOrderRepository orders, QuoteRepository quotes,
            ReservationService reservations, FileStorageService files, OrderHistoryService history,
            NotificationService notifications, Clock clock) {
        this.payments = payments;
        this.orders = orders;
        this.quotes = quotes;
        this.reservations = reservations;
        this.files = files;
        this.history = history;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaymentSummary summary(Long orderId) {
        Optional<Quote> accepted = acceptedQuote(orderId);
        List<Payment> list = payments.findByOrderIdOrderByCreatedAtAscIdAsc(orderId);
        BigDecimal received = received(list);
        BigDecimal total = accepted.map(Quote::getTotal).orElse(null);
        BigDecimal balanceDue = total == null ? null : total.subtract(received).max(BigDecimal.ZERO);
        return new PaymentSummary(total, accepted.map(Quote::getDepositAmount).orElse(null), received, balanceDue,
                statusFor(accepted.orElse(null), list), list.stream().anyMatch(p -> p.getStatus() == Payment.Status.PENDING_REVIEW),
                list.stream().map(PaymentView::from).toList());
    }

    /** Comprovante do sinal enviado pela cliente pelo acompanhamento. */
    @Transactional
    public PaymentSummary submitProof(Long orderId, MultipartFile file, BigDecimal amount) {
        CakeOrder order = lockOrder(orderId);
        if (order.getCommercialStatus() != CommercialStatus.AWAITING_DEPOSIT) {
            throw new ConflictException("PROOF_NOT_EXPECTED", "Este pedido não está aguardando o pagamento do sinal.");
        }
        if (payments.existsByOrderIdAndStatus(orderId, Payment.Status.PENDING_REVIEW)) {
            throw new ConflictException("PROOF_ALREADY_SUBMITTED",
                    "Já existe um comprovante em análise. Aguarde a conferência da confeitaria.");
        }
        Quote quote = acceptedQuote(orderId)
                .orElseThrow(() -> new ConflictException("PROOF_NOT_EXPECTED", "Não há orçamento aceito para este pedido."));
        BigDecimal value = amount == null ? quote.getDepositAmount() : amount;
        if (value.signum() <= 0 || value.compareTo(quote.getTotal()) > 0) {
            throw new FieldValidationException("amount", "Informe o valor pago.");
        }

        var validated = files.validate(file, FilePurpose.PAYMENT_PROOF, "proof");
        Long fileId = files.store(validated, orderId, FilePurpose.PAYMENT_PROOF, ActorType.CLIENT).getId();
        Payment payment = payments.saveAndFlush(Payment.proofFromClient(orderId, value, fileId));

        refreshStatus(order);
        history.record(orderId, "PROOF_SUBMITTED", ActorType.CLIENT, "Comprovante do sinal enviado pela cliente.",
                Map.of("amount", value.toPlainString(), "paymentId", payment.getId()));
        notifications.notify(NotificationType.PROOF_SUBMITTED, orderId,
                firstName(order) + " enviou o comprovante do sinal (R$ " + value.toPlainString() + "). Confira e confirme no painel.",
                "PROOF_SUBMITTED:" + payment.getId());
        return summary(orderId);
    }

    /**
     * Confirma o comprovante. Se for o sinal e o valor recebido alcançar o sinal (ou a proprietária
     * aceitar um valor menor como sinal), o pedido é confirmado e a vaga ocupada. Se o horário estiver
     * lotado, nada é gravado (SLOT_FULL) — a proprietária pode remarcar o pedido e confirmar de novo.
     */
    @Transactional
    public PaymentSummary confirm(Long paymentId, String note, boolean acceptAsDeposit, Long actorId) {
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new NotFoundException("Pagamento não encontrado."));
        CakeOrder order = lockOrder(payment.getOrderId());
        payment.confirm(blankToNull(note), clock.instant());
        payments.saveAndFlush(payment);
        history.record(order.getId(), "PAYMENT_CONFIRMED", ActorType.OWNER,
                "Pagamento de R$ " + payment.getAmount().toPlainString() + " confirmado.",
                withActor(Map.of("paymentId", paymentId, "kind", payment.getKind().name()), actorId));
        confirmOrderIfDepositReached(order, acceptAsDeposit, actorId);
        refreshStatus(order);
        return summary(order.getId());
    }

    @Transactional
    public PaymentSummary reject(Long paymentId, String reason, Long actorId) {
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new NotFoundException("Pagamento não encontrado."));
        CakeOrder order = lockOrder(payment.getOrderId());
        payment.reject(reason.strip(), clock.instant());
        payments.saveAndFlush(payment);
        refreshStatus(order);
        history.record(order.getId(), "PAYMENT_REJECTED", ActorType.OWNER, "Comprovante não aceito: " + reason.strip(),
                withActor(Map.of("paymentId", paymentId), actorId));
        return summary(order.getId());
    }

    /** Pagamento recebido diretamente pela proprietária (ex.: sinal em dinheiro, saldo na retirada, reembolso). */
    @Transactional
    public PaymentSummary register(Long orderId, RegisterRequest request, Long actorId) {
        CakeOrder order = lockOrder(orderId);
        if (acceptedQuote(orderId).isEmpty() && request.kind() != Payment.Kind.REFUND) {
            throw new ConflictException("NO_ACCEPTED_QUOTE", "Registre pagamentos depois que a cliente aceitar o orçamento.");
        }
        if (request.kind() == Payment.Kind.REFUND
                && request.amount().compareTo(received(payments.findByOrderIdOrderByCreatedAtAscIdAsc(orderId))) > 0) {
            throw new FieldValidationException("amount", "O reembolso não pode ser maior que o valor recebido.");
        }
        Payment payment = payments.saveAndFlush(Payment.registeredByOwner(orderId, request.kind(), request.method(),
                request.amount(), blankToNull(request.notes()), clock.instant()));
        String label = switch (request.kind()) {
            case DEPOSIT -> "Sinal";
            case BALANCE -> "Saldo";
            case REFUND -> "Reembolso";
        };
        history.record(orderId, "PAYMENT_REGISTERED", ActorType.OWNER,
                label + " de R$ " + payment.getAmount().toPlainString() + " registrado ("
                        + (request.method() == Payment.Method.PIX ? "Pix" : "dinheiro") + ").",
                withActor(Map.of("paymentId", payment.getId(), "kind", request.kind().name()), actorId));
        if (request.kind() != Payment.Kind.REFUND) {
            confirmOrderIfDepositReached(order, request.acceptsAsDeposit(), actorId);
        }
        refreshStatus(order);
        return summary(orderId);
    }

    /** Recalcula a situação consolidada dos pagamentos no pedido. */
    @Transactional
    public void refreshStatus(CakeOrder order) {
        order.updatePaymentStatus(statusFor(acceptedQuote(order.getId()).orElse(null),
                payments.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId())));
        orders.saveAndFlush(order);
    }

    @Transactional(readOnly = true)
    public boolean hasConfirmedPayments(Long orderId) {
        return payments.existsByOrderIdAndStatus(orderId, Payment.Status.CONFIRMED);
    }

    @Transactional(readOnly = true)
    public boolean hasProofUnderReview(Long orderId) {
        return payments.existsByOrderIdAndStatus(orderId, Payment.Status.PENDING_REVIEW);
    }

    private void confirmOrderIfDepositReached(CakeOrder order, boolean acceptAsDeposit, Long actorId) {
        if (order.getCommercialStatus() != CommercialStatus.AWAITING_DEPOSIT) {
            return;
        }
        Quote quote = acceptedQuote(order.getId()).orElseThrow();
        BigDecimal received = received(payments.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId()));
        if (received.compareTo(quote.getDepositAmount()) < 0 && !acceptAsDeposit) {
            return;
        }
        reservations.reserve(order.getId(), order.getPickupDate(), order.getPickupTime());
        order.transitionTo(CommercialStatus.CONFIRMED);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("received", received.toPlainString());
        details.put("deposit", quote.getDepositAmount().toPlainString());
        details.put("belowDeposit", received.compareTo(quote.getDepositAmount()) < 0);
        history.record(order.getId(), "CONFIRMED", ActorType.OWNER,
                "Sinal confirmado: pedido confirmado e horário reservado.", withActor(details, actorId));
    }

    static PaymentStatus statusFor(Quote accepted, List<Payment> list) {
        boolean pending = list.stream().anyMatch(p -> p.getStatus() == Payment.Status.PENDING_REVIEW);
        if (accepted == null) {
            return pending ? PaymentStatus.DEPOSIT_UNDER_REVIEW : PaymentStatus.NONE;
        }
        BigDecimal received = received(list);
        if (received.compareTo(accepted.getTotal()) >= 0) {
            return PaymentStatus.PAID;
        }
        if (received.compareTo(accepted.getDepositAmount()) >= 0) {
            return PaymentStatus.DEPOSIT_PAID;
        }
        return pending ? PaymentStatus.DEPOSIT_UNDER_REVIEW : PaymentStatus.NONE;
    }

    private static BigDecimal received(List<Payment> list) {
        return list.stream().filter(p -> p.getStatus() == Payment.Status.CONFIRMED)
                .map(Payment::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Optional<Quote> acceptedQuote(Long orderId) {
        return quotes.findCurrent(orderId).filter(q -> q.getStatus() == QuoteStatus.ACCEPTED);
    }

    private CakeOrder lockOrder(Long orderId) {
        return orders.findByIdForUpdate(orderId).orElseThrow(() -> new NotFoundException("Pedido não encontrado."));
    }

    private static String firstName(CakeOrder order) {
        return order.getCustomerName().split(" ")[0];
    }

    private static Map<String, Object> withActor(Map<String, Object> details, Long actorId) {
        Map<String, Object> result = new LinkedHashMap<>(details);
        result.put("actorId", actorId);
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
