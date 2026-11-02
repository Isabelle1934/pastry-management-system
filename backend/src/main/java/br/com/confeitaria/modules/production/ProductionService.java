package br.com.confeitaria.modules.production;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.exception.ConflictException;
import br.com.confeitaria.common.exception.NotFoundException;
import br.com.confeitaria.common.persistence.Versions;
import br.com.confeitaria.common.validation.WhatsAppNumber;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.OrderDetail;
import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.order.entity.CommercialStatus;
import br.com.confeitaria.modules.order.entity.ProductionStatus;
import br.com.confeitaria.modules.order.repository.CakeOrderRepository;
import br.com.confeitaria.modules.order.service.OrderAdminService;
import br.com.confeitaria.modules.order.service.OrderHistoryService;
import br.com.confeitaria.modules.order.service.TrackingCodes;
import br.com.confeitaria.modules.payment.dto.PaymentDtos.PaymentSummary;
import br.com.confeitaria.modules.payment.service.PaymentService;
import br.com.confeitaria.modules.production.ProductionDtos.ChangeRequest;
import br.com.confeitaria.modules.production.ProductionDtos.PickupBoard;
import br.com.confeitaria.modules.production.ProductionDtos.PickupItem;

/** Andamento da produção (em produção → pronto → entregue) e lista de próximas retiradas. */
@Service
public class ProductionService {

    static final int DEFAULT_DAYS = 14;
    static final int MAX_DAYS = 60;

    private static final Map<ProductionStatus, String> DESCRIPTIONS = Map.of(
            ProductionStatus.NOT_STARTED, "Produção marcada como não iniciada.",
            ProductionStatus.IN_PRODUCTION, "Bolo em produção.",
            ProductionStatus.READY, "Bolo pronto para retirada.",
            ProductionStatus.DELIVERED, "Bolo entregue: pedido concluído.");

    private final CakeOrderRepository orders;
    private final OrderAdminService orderAdmin;
    private final PaymentService payments;
    private final OrderHistoryService history;
    private final Clock clock;

    public ProductionService(CakeOrderRepository orders, OrderAdminService orderAdmin, PaymentService payments,
            OrderHistoryService history, Clock clock) {
        this.orders = orders;
        this.orderAdmin = orderAdmin;
        this.payments = payments;
        this.history = history;
        this.clock = clock;
    }

    /** Pedidos confirmados com retirada até {@code days} dias à frente, incluindo os atrasados. */
    @Transactional(readOnly = true)
    public PickupBoard board(int days) {
        LocalDate today = LocalDate.now(clock);
        LocalDate until = today.plusDays(Math.clamp(days, 0, MAX_DAYS));
        List<PickupItem> items = orders
                .findByCommercialStatusAndPickupDateLessThanEqualOrderByPickupDateAscPickupTimeAscIdAsc(
                        CommercialStatus.CONFIRMED, until)
                .stream().map(order -> toItem(order, payments.summary(order.getId()), today)).toList();
        return new PickupBoard(today, until, items);
    }

    /**
     * Muda o andamento. Entregar com saldo em aberto é permitido (P8), mas exige confirmação explícita
     * ({@code confirmBalanceDue}) e fica registrado no histórico.
     */
    @Transactional
    public OrderDetail change(Long orderId, ChangeRequest request, Long actorId) {
        CakeOrder order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Pedido não encontrado."));
        Versions.require(order.getVersion(), request.version());
        ProductionStatus target = request.status();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", order.getProductionStatus().name());
        details.put("to", target.name());
        if (target == ProductionStatus.DELIVERED && order.getCommercialStatus() == CommercialStatus.CONFIRMED) {
            BigDecimal balanceDue = payments.summary(orderId).balanceDue();
            if (balanceDue != null && balanceDue.signum() > 0) {
                if (!request.confirmsBalanceDue()) {
                    throw new ConflictException("BALANCE_DUE", "Ainda falta receber R$ "
                            + balanceDue.toPlainString().replace('.', ',')
                            + ". Registre o pagamento ou confirme a entrega com saldo em aberto.");
                }
                details.put("balanceDue", balanceDue.toPlainString());
            }
        }
        order.changeProduction(target);
        orders.saveAndFlush(order);

        String note = request.note() == null ? "" : request.note().strip();
        if (!note.isEmpty()) {
            details.put("note", note);
        }
        details.put("actorId", actorId);
        history.record(orderId, "PRODUCTION_" + target.name(), ActorType.OWNER, DESCRIPTIONS.get(target), details);
        return orderAdmin.detail(orderId);
    }

    private static PickupItem toItem(CakeOrder order, PaymentSummary summary, LocalDate today) {
        return new PickupItem(order.getId(), TrackingCodes.format(order.getTrackingCode()), order.getVersion(),
                order.getCustomerName(), WhatsAppNumber.format(order.getCustomerWhatsapp()),
                "https://wa.me/" + order.getCustomerWhatsapp().substring(1), order.getPickupDate(),
                order.getPickupTime().toString(), order.getSizeNameSnapshot(), order.getCakeText(),
                order.isTopperRequested(), order.getProductionStatus(), order.getPaymentStatus(), summary.total(),
                summary.balanceDue(), order.getPickupDate().isBefore(today));
    }
}
