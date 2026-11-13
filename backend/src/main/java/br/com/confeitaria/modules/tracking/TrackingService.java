package br.com.confeitaria.modules.tracking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.exception.ConflictException;
import br.com.confeitaria.common.exception.NotFoundException;
import br.com.confeitaria.common.persistence.Versions;
import br.com.confeitaria.modules.file.repository.StoredFileRepository;
import br.com.confeitaria.modules.notification.entity.NotificationType;
import br.com.confeitaria.modules.notification.service.NotificationService;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.Choice;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.OrderItemView;
import br.com.confeitaria.modules.order.dto.PublicOrderDtos.ClientEditRequest;
import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.order.entity.CommercialStatus;
import br.com.confeitaria.modules.order.repository.CakeOrderRepository;
import br.com.confeitaria.modules.order.service.OrderDetailsApplier;
import br.com.confeitaria.modules.order.service.OrderHistoryService;
import br.com.confeitaria.modules.order.service.TrackingCodes;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.QuoteView;
import br.com.confeitaria.modules.quote.repository.QuoteRepository;
import br.com.confeitaria.modules.quote.service.QuoteService;
import br.com.confeitaria.modules.payment.dto.PaymentDtos.PaymentSummary;
import br.com.confeitaria.modules.payment.entity.Payment;
import br.com.confeitaria.modules.payment.service.PaymentService;
import br.com.confeitaria.modules.settings.service.SettingsService;
import br.com.confeitaria.modules.tracking.TrackingDtos.ClientPayment;
import br.com.confeitaria.modules.tracking.TrackingDtos.ClientPaymentEntry;
import br.com.confeitaria.modules.tracking.TrackingDtos.ClientQuote;
import br.com.confeitaria.modules.tracking.TrackingDtos.TrackingView;

/** O que a cliente pode ver e fazer no próprio pedido. */
@Service
public class TrackingService {

    private final CakeOrderRepository orders;
    private final QuoteRepository quotes;
    private final QuoteService quoteService;
    private final StoredFileRepository files;
    private final OrderDetailsApplier detailsApplier;
    private final OrderHistoryService history;
    private final NotificationService notifications;
    private final PaymentService payments;
    private final SettingsService settings;

    public TrackingService(CakeOrderRepository orders, QuoteRepository quotes, QuoteService quoteService,
            StoredFileRepository files, OrderDetailsApplier detailsApplier, OrderHistoryService history,
            NotificationService notifications, PaymentService payments, SettingsService settings) {
        this.orders = orders;
        this.quotes = quotes;
        this.quoteService = quoteService;
        this.files = files;
        this.detailsApplier = detailsApplier;
        this.history = history;
        this.notifications = notifications;
        this.payments = payments;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public TrackingView view(Long orderId) {
        CakeOrder order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Pedido não encontrado."));
        ClientQuote quote = quotes.findCurrent(orderId).map(QuoteView::from)
                .map(q -> new ClientQuote(q.id(), q.versionNumber(), q.status(), q.items(), q.breakdown(), q.total(), q.depositAmount(),
                        q.balanceAmount(), q.notes(), q.validUntil()))
                .orElse(null);
        return new TrackingView(TrackingCodes.format(order.getTrackingCode()), order.getCustomerName(),
                mask(order.getCustomerWhatsapp()), order.getPickupDate(), order.getPickupTime().toString(),
                order.isCustomCake(), OrderItemView.of(order), order.getFulfillmentType(), order.getDeliveryAddress(),
                order.getDeliveryDistrict(), order.getDeliveryReference(),
                order.getSize() == null ? null : order.getSize().getId(), order.getSizeNameSnapshot(), choices(order.getFlavorChoices()),
                choices(order.getFillingChoices()), order.getDecoration(), order.getCakeText(), order.getNotes(),
                order.isTopperRequested(), order.getTopperTheme(), order.getTopperName(), order.getTopperAge(),
                files.findByOrderIdAndDeletedAtIsNullOrderByIdAsc(orderId).size(), order.getCommercialStatus(),
                order.getPaymentStatus(), order.getProductionStatus(), order.isEditableByClient(), order.getVersion(),
                quote, payment(order), order.getDeliveryZone(),
                order.getCakeCategory() == null ? null : order.getCakeCategory().getId(), order.getCakeCategorySnapshot(),
                order.getCustomerEmail() != null);
    }

    /** Dados de pagamento para a cliente: valores, situação, chave Pix (quando há algo a pagar) e comprovantes. */
    private ClientPayment payment(CakeOrder order) {
        PaymentSummary summary = payments.summary(order.getId());
        if (summary.total() == null) {
            return null;
        }
        boolean awaitingDeposit = order.getCommercialStatus() == CommercialStatus.AWAITING_DEPOSIT;
        boolean somethingToPay = awaitingDeposit
                || (order.getCommercialStatus() == CommercialStatus.CONFIRMED && summary.balanceDue().signum() > 0);
        var bakery = settings.current();
        return new ClientPayment(summary.total(), summary.depositAmount(), summary.received(), summary.balanceDue(),
                summary.status(), somethingToPay ? bakery.getPixKey() : null,
                somethingToPay ? bakery.getPixHolderName() : null,
                awaitingDeposit ? bakery.getCardPaymentLink() : null, summary.depositDueAt(),
                awaitingDeposit && !summary.proofUnderReview(),
                summary.payments().stream()
                        .filter(p -> p.kind() != Payment.Kind.REFUND || p.status() == Payment.Status.CONFIRMED)
                        .map(p -> new ClientPaymentEntry(p.kind(), p.method(), p.amount(), p.status(), p.reviewNote(),
                                p.createdAt(), p.proofFileId()))
                        .toList());
    }

    /**
     * Edição pela cliente antes do aceite. Se havia orçamento enviado, ele deixa de valer e o pedido volta
     * para análise (pendência P5, proposta padrão): a proprietária precisa reavaliar o novo pedido.
     */
    @Transactional
    public TrackingView edit(Long orderId, ClientEditRequest request) {
        CakeOrder order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Pedido não encontrado."));
        Versions.require(order.getVersion(), request.version());
        if (!order.isEditableByClient()) {
            throw new ConflictException("ORDER_NOT_EDITABLE",
                    "Depois do aceite do orçamento, alterações são feitas pela confeitaria. Fale conosco pelo WhatsApp.");
        }
        Map<String, Object> changes = new LinkedHashMap<>(
                detailsApplier.apply(order, request.details(), OrderDetailsApplier.Actor.CLIENT));
        if (changes.isEmpty()) {
            return view(orderId);
        }
        boolean quoteInvalidated = quoteService.supersedePendingQuote(orderId);
        if (order.getCommercialStatus() == CommercialStatus.QUOTE_SENT) {
            order.transitionTo(CommercialStatus.PENDING_REVIEW);
        }
        orders.saveAndFlush(order);
        changes.put("quoteInvalidated", quoteInvalidated);
        history.record(orderId, "EDITED_BY_CLIENT", ActorType.CLIENT,
                quoteInvalidated ? "Pedido alterado pela cliente; o orçamento enviado deixou de valer."
                        : "Pedido alterado pela cliente.",
                changes);
        notifications.notify(NotificationType.ORDER_EDITED_BY_CLIENT, orderId,
                order.getCustomerName().split(" ")[0] + " alterou o pedido"
                        + (quoteInvalidated ? "; o orçamento enviado deixou de valer e precisa ser refeito." : "."),
                "ORDER_EDITED_BY_CLIENT:" + orderId + ":" + order.getVersion());
        return view(orderId);
    }

    @Transactional
    public TrackingView accept(Long orderId, Long quoteId) {
        quoteService.accept(orderId, quoteId);
        return view(orderId);
    }

    @Transactional
    public TrackingView decline(Long orderId, Long quoteId, String reason) {
        quoteService.decline(orderId, quoteId, reason);
        return view(orderId);
    }

    /** "+5511987654321" → "(11) *****-4321" */
    static String mask(String e164) {
        String local = e164.substring(3);
        return "(" + local.substring(0, 2) + ") " + "*".repeat(local.length() - 6) + "-" + local.substring(local.length() - 4);
    }

    private static List<Choice> choices(Map<Long, String> map) {
        return map.entrySet().stream().map(e -> new Choice(e.getKey(), e.getValue())).toList();
    }
}
