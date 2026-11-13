package br.com.confeitaria.modules.order.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.audit.Changes;
import br.com.confeitaria.common.exception.ConflictException;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.common.exception.NotFoundException;
import br.com.confeitaria.common.persistence.Versions;
import br.com.confeitaria.common.validation.WhatsAppNumber;
import br.com.confeitaria.modules.file.repository.StoredFileRepository;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.Choice;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.FileInfo;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.HistoryEntry;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.OrderDetail;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.OrderItemView;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.OrderSummary;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.PageResponse;
import br.com.confeitaria.modules.order.dto.PublicOrderDtos.OrderRequest;
import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.order.entity.CommercialStatus;
import br.com.confeitaria.modules.order.repository.CakeOrderRepository;
import br.com.confeitaria.modules.quote.entity.Quote;
import br.com.confeitaria.modules.payment.service.PaymentService;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.QuoteView;
import br.com.confeitaria.modules.quote.repository.QuoteRepository;
import br.com.confeitaria.modules.quote.service.QuoteService;
import br.com.confeitaria.modules.schedule.service.ReservationService;
import br.com.confeitaria.modules.settings.service.SettingsService;
import jakarta.persistence.criteria.Predicate;

/** Gestão de pedidos pela proprietária: consulta, edição, recusa e cancelamento. */
@Service
public class OrderAdminService {

    public record ListFilter(CommercialStatus status, String query, LocalDate from, LocalDate to) {
    }

    private final CakeOrderRepository orders;
    private final QuoteRepository quotes;
    private final QuoteService quoteService;
    private final StoredFileRepository files;
    private final OrderHistoryService history;
    private final OrderDetailsApplier detailsApplier;
    private final ReservationService reservations;
    private final PaymentService payments;
    private final SettingsService settings;
    private final Clock clock;

    public OrderAdminService(CakeOrderRepository orders, QuoteRepository quotes, QuoteService quoteService,
            StoredFileRepository files, OrderHistoryService history, OrderDetailsApplier detailsApplier,
            ReservationService reservations, PaymentService payments, SettingsService settings, Clock clock) {
        this.settings = settings;
        this.orders = orders;
        this.quotes = quotes;
        this.quoteService = quoteService;
        this.files = files;
        this.history = history;
        this.detailsApplier = detailsApplier;
        this.reservations = reservations;
        this.payments = payments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> list(ListFilter filter, int page, int size) {
        Sort sort = filter.status() == null
                ? Sort.by(Sort.Direction.DESC, "createdAt")
                : Sort.by(Sort.Direction.ASC, "pickupDate", "pickupTime");
        Page<CakeOrder> result = orders.findAll(specification(filter),
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), sort));
        Map<Long, Quote> current = quotes.currentByOrder(result.getContent().stream().map(CakeOrder::getId).toList());
        return new PageResponse<>(result.getContent().stream()
                .map(o -> OrderSummary.from(o, current.get(o.getId()))).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Map<CommercialStatus, Long> countByStatus() {
        Map<CommercialStatus, Long> counts = new EnumMap<>(CommercialStatus.class);
        for (CommercialStatus status : CommercialStatus.values()) {
            counts.put(status, 0L);
        }
        orders.countByStatus().forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return counts;
    }

    @Transactional(readOnly = true)
    public OrderDetail detail(Long id) {
        CakeOrder order = find(id);
        List<Choice> flavors = toChoices(order.getFlavorChoices());
        List<Choice> fillings = toChoices(order.getFillingChoices());
        return new OrderDetail(order.getId(), TrackingCodes.format(order.getTrackingCode()), order.getVersion(),
                order.getCustomerName(), WhatsAppNumber.format(order.getCustomerWhatsapp()),
                "https://wa.me/" + order.getCustomerWhatsapp().substring(1), order.getPickupDate(),
                order.getPickupTime().toString(), order.isCustomCake(), OrderItemView.of(order),
                order.getFulfillmentType(), order.getDeliveryAddress(), order.getDeliveryDistrict(),
                order.getDeliveryReference(), order.getSize() == null ? null : order.getSize().getId(),
                order.getSizeNameSnapshot(), flavors, fillings,
                order.getDecoration(), order.getCakeText(), order.getNotes(), order.isTopperRequested(),
                order.getTopperTheme(), order.getTopperName(), order.getTopperAge(), order.getCommercialStatus(),
                order.getPaymentStatus(), order.getProductionStatus(), order.getCancelledAt(),
                order.getCancellationReason(), order.getCancelledBy(),
                reservations.activeReservation(order.getId()).isPresent(),
                files.findByOrderIdAndDeletedAtIsNullOrderByIdAsc(order.getId()).stream().map(FileInfo::from).toList(),
                quoteService.list(order.getId()), payments.summary(order.getId()),
                history.historyOf(order.getId()).stream().map(HistoryEntry::from).toList(),
                order.getCreatedAt(), order.getUpdatedAt(), order.getCustomerEmail(), order.getDeliveryZone(),
                order.getCakeCategory() == null ? null : order.getCakeCategory().getId(),
                order.getCakeCategorySnapshot(), order.getSizePriceSnapshot(),
                DeliveryFees.suggested(settings.current(), order.getFulfillmentType(), order.getDeliveryZone(),
                        order.isCustomCake()));
    }

    /**
     * Edição pela proprietária (inclusive depois do aceite do orçamento). Se o pedido já ocupa vaga
     * e o horário muda, a vaga é remarcada na mesma transação (falha se o novo horário estiver lotado).
     */
    @Transactional
    public OrderDetail update(Long id, OrderRequest request, Long actorId) {
        CakeOrder order = lock(id);
        if (request.version() == null) {
            throw new FieldValidationException("version", "Versão obrigatória.");
        }
        Versions.require(order.getVersion(), request.version());
        if (order.isClosed()) {
            throw new ConflictException("ORDER_CLOSED", "Pedidos encerrados não podem ser editados.");
        }

        String name = OrderCreationService.normalizeName(request.customerName());
        String whatsapp = OrderCreationService.normalizeWhatsApp(request.customerWhatsapp());
        var changes = new Changes()
                .track("customerName", order.getCustomerName(), name)
                .trackWithoutValues("customerWhatsapp", order.getCustomerWhatsapp(), whatsapp)
                .trackWithoutValues("customerEmail", order.getCustomerEmail(),
                        OrderCreationService.normalizeEmail(request.customerEmail()))
                .asMap();
        Map<String, Object> allChanges = new LinkedHashMap<>(changes);
        order.updateCustomer(name, whatsapp);
        order.setCustomerEmail(OrderCreationService.normalizeEmail(request.customerEmail()));
        allChanges.putAll(detailsApplier.apply(order, request.details(), OrderDetailsApplier.Actor.OWNER));

        if (reservations.activeReservation(id).isPresent()) {
            reservations.reserve(id, order.getPickupDate(), order.getPickupTime());
        }
        orders.saveAndFlush(order);
        if (!allChanges.isEmpty()) {
            history.record(id, "EDITED_BY_OWNER", ActorType.OWNER, "Pedido editado pela proprietária.",
                    withActor(allChanges, actorId));
        }
        return detail(id);
    }

    /** Recusa a solicitação (antes do aceite do orçamento). */
    @Transactional
    public OrderDetail reject(Long id, String reason, long version, Long actorId) {
        CakeOrder order = lock(id);
        Versions.require(order.getVersion(), version);
        order.transitionTo(CommercialStatus.REJECTED);
        quoteService.supersedePendingQuote(id);
        orders.saveAndFlush(order);
        history.record(id, "REJECTED", ActorType.OWNER, "Solicitação recusada pela proprietária.",
                withActor(Map.of("reason", reason.strip()), actorId));
        return detail(id);
    }

    /** Cancela o pedido e libera a vaga na agenda, se ocupada. Pagamentos já registrados são mantidos. */
    @Transactional
    public OrderDetail cancel(Long id, String reason, long version, Long actorId) {
        CakeOrder order = lock(id);
        Versions.require(order.getVersion(), version);
        order.cancel(ActorType.OWNER, reason.strip(), clock.instant());
        quoteService.supersedePendingQuote(id);
        boolean released = reservations.release(id, "Pedido cancelado: " + reason.strip());
        orders.saveAndFlush(order);
        history.record(id, "CANCELLED", ActorType.OWNER, "Pedido cancelado pela proprietária.",
                withActor(Map.of("reason", reason.strip(), "slotReleased", released), actorId));
        return detail(id);
    }

    CakeOrder find(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Pedido não encontrado."));
    }

    private CakeOrder lock(Long id) {
        return orders.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Pedido não encontrado."));
    }

    private static Map<String, Object> withActor(Map<String, Object> details, Long actorId) {
        Map<String, Object> result = new LinkedHashMap<>(details);
        result.put("actorId", actorId);
        return result;
    }

    private static List<Choice> toChoices(Map<Long, String> choices) {
        List<Choice> result = new ArrayList<>();
        choices.forEach((id, name) -> result.add(new Choice(id, name)));
        return result;
    }

    /** Filtros combinados; a busca aceita nome, código (com ou sem hífens) ou dígitos do WhatsApp. */
    private static Specification<CakeOrder> specification(ListFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("commercialStatus"), filter.status()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("pickupDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("pickupDate"), filter.to()));
            }
            String text = filter.query() == null ? "" : filter.query().strip();
            if (!text.isEmpty()) {
                List<Predicate> any = new ArrayList<>();
                any.add(cb.like(cb.lower(root.get("customerName")), "%" + escape(text.toLowerCase(Locale.ROOT)) + "%", '\\'));
                String code = text.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
                if (code.length() >= 4) {
                    any.add(cb.like(root.get("trackingCode"), "%" + escape(code) + "%", '\\'));
                }
                String digits = text.replaceAll("\\D", "");
                if (digits.length() >= 4) {
                    any.add(cb.like(root.get("customerWhatsapp"), "%" + digits + "%"));
                }
                predicates.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Usado pelo envio de orçamento no controller para devolver o pedido atualizado. */
    @Transactional(readOnly = true)
    public List<QuoteView> quotes(Long orderId) {
        find(orderId);
        return quoteService.list(orderId);
    }
}
