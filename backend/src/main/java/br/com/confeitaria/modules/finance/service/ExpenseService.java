package br.com.confeitaria.modules.finance.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.audit.AuditService;
import br.com.confeitaria.common.audit.Changes;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.common.exception.NotFoundException;
import br.com.confeitaria.common.persistence.Versions;
import br.com.confeitaria.config.AppProperties;
import br.com.confeitaria.modules.finance.dto.FinanceDtos.ExpenseRequest;
import br.com.confeitaria.modules.finance.dto.FinanceDtos.ExpenseView;
import br.com.confeitaria.modules.finance.dto.FinanceDtos.OrderRef;
import br.com.confeitaria.modules.finance.entity.Expense;
import br.com.confeitaria.modules.finance.entity.ExpenseCategory;
import br.com.confeitaria.modules.finance.repository.ExpenseRepository;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.PageResponse;
import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.order.repository.CakeOrderRepository;
import br.com.confeitaria.modules.order.service.TrackingCodes;

/** Despesas gerais e vinculadas a pedidos. Exclusão apenas lógica, sempre auditada. */
@Service
public class ExpenseService {

    private static final String ENTITY = "expense";

    private final ExpenseRepository expenses;
    private final ExpenseCategoryService categories;
    private final CakeOrderRepository orders;
    private final AuditService audit;
    private final Clock clock;
    private final AppProperties properties;

    public ExpenseService(ExpenseRepository expenses, ExpenseCategoryService categories, CakeOrderRepository orders,
            AuditService audit, Clock clock, AppProperties properties) {
        this.expenses = expenses;
        this.categories = categories;
        this.orders = orders;
        this.audit = audit;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseView> list(LocalDate from, LocalDate to, Long categoryId, Long orderId, int page,
            int size) {
        Periods.Range range = Periods.resolve(from, to, LocalDate.ofInstant(clock.instant(), properties.timezone()));
        Page<Expense> result = expenses.search(range.from(), range.to(), categoryId, orderId,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                        Sort.by(Sort.Direction.DESC, "expenseDate", "id")));
        Map<Long, OrderRef> refs = orderRefs(result.getContent().stream().map(Expense::getOrderId).toList());
        return new PageResponse<>(result.getContent().stream()
                .map(e -> ExpenseView.from(e, e.getOrderId() == null ? null : refs.get(e.getOrderId()))).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ExpenseView create(ExpenseRequest request, Long actorId) {
        ExpenseCategory category = activeCategory(request.categoryId(), null);
        CakeOrder order = orderFor(request.orderCode());
        Expense expense = expenses.save(new Expense(request.description(), request.amount(), request.expenseDate(),
                category, order == null ? null : order.getId(), request.notes()));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("amount", expense.getAmount().toPlainString());
        details.put("expenseDate", expense.getExpenseDate().toString());
        if (order != null) {
            details.put("orderId", order.getId());
        }
        audit.record(ActorType.OWNER, actorId, "EXPENSE_CREATED", ENTITY, expense.getId(), details);
        return ExpenseView.from(expense, ref(order));
    }

    @Transactional
    public ExpenseView update(Long id, ExpenseRequest request, Long actorId) {
        Expense expense = find(id);
        if (request.version() == null) {
            throw new FieldValidationException("version", "Versão obrigatória.");
        }
        Versions.require(expense.getVersion(), request.version());
        ExpenseCategory category = activeCategory(request.categoryId(), expense.getCategory());
        CakeOrder order = orderFor(request.orderCode());
        Long orderId = order == null ? null : order.getId();
        Changes changes = new Changes()
                .track("description", expense.getDescription(), request.description().strip())
                .track("amount", expense.getAmount().toPlainString(), request.amount().setScale(2).toPlainString())
                .track("expenseDate", expense.getExpenseDate().toString(), request.expenseDate().toString())
                .track("categoryId", expense.getCategory().getId(), category.getId())
                .track("orderId", expense.getOrderId(), orderId);
        expense.update(request.description(), request.amount(), request.expenseDate(), category, orderId,
                request.notes());
        expenses.flush();
        if (!changes.isEmpty()) {
            audit.record(ActorType.OWNER, actorId, "EXPENSE_UPDATED", ENTITY, id, changes.asMap());
        }
        return ExpenseView.from(expense, ref(order));
    }

    /** Exclusão lógica: some dos totais e das listas, mas o registro e a auditoria permanecem. */
    @Transactional
    public void delete(Long id, long version, Long actorId) {
        Expense expense = find(id);
        Versions.require(expense.getVersion(), version);
        expense.delete(clock.instant());
        expenses.flush();
        audit.record(ActorType.OWNER, actorId, "EXPENSE_DELETED", ENTITY, id,
                Map.of("amount", expense.getAmount().toPlainString(), "description", expense.getDescription()));
    }

    private Expense find(Long id) {
        return expenses.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Despesa não encontrada."));
    }

    /** Categorias desativadas não recebem despesas novas, mas a despesa antiga pode mantê-la ao ser editada. */
    private ExpenseCategory activeCategory(Long categoryId, ExpenseCategory current) {
        ExpenseCategory category;
        try {
            category = categories.find(categoryId);
        } catch (NotFoundException e) {
            throw new FieldValidationException("categoryId", "Categoria não encontrada.");
        }
        if (!category.isActive() && (current == null || !current.getId().equals(category.getId()))) {
            throw new FieldValidationException("categoryId", "Esta categoria está desativada.");
        }
        return category;
    }

    private CakeOrder orderFor(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return TrackingCodes.normalize(code).flatMap(orders::findByTrackingCode)
                .orElseThrow(() -> new FieldValidationException("orderCode", "Pedido não encontrado com este código."));
    }

    private Map<Long, OrderRef> orderRefs(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return orders.findAllById(distinct).stream().map(ExpenseService::ref)
                .collect(Collectors.toMap(OrderRef::id, Function.identity()));
    }

    private static OrderRef ref(CakeOrder order) {
        return order == null ? null
                : new OrderRef(order.getId(), TrackingCodes.format(order.getTrackingCode()), order.getCustomerName());
    }
}
