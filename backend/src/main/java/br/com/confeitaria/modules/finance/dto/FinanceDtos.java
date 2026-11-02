package br.com.confeitaria.modules.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import br.com.confeitaria.modules.finance.entity.Expense;
import br.com.confeitaria.modules.finance.entity.ExpenseCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos do financeiro: categorias, despesas e resumo do período. */
public final class FinanceDtos {

    private FinanceDtos() {
    }

    /** {@code version} é obrigatório na edição e ignorado na criação. */
    public record CategoryRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 60, message = "Máximo de 60 caracteres.") String name,
            Long version) {
    }

    public record CategoryResponse(Long id, String name, boolean active, long version) {

        public static CategoryResponse from(ExpenseCategory category) {
            return new CategoryResponse(category.getId(), category.getName(), category.isActive(), category.getVersion());
        }
    }

    /**
     * @param orderCode código de acompanhamento do pedido ao qual a despesa pertence (opcional)
     */
    public record ExpenseRequest(
            @NotBlank(message = "Descreva a despesa.") @Size(max = 200, message = "Máximo de 200 caracteres.") String description,
            @NotNull(message = "Informe o valor.")
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
            @DecimalMax(value = "999999.99", message = "Valor muito alto.")
            @Digits(integer = 6, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal amount,
            @NotNull(message = "Informe a data.") LocalDate expenseDate,
            @NotNull(message = "Escolha a categoria.") Long categoryId,
            @Size(max = 30, message = "Código inválido.") String orderCode,
            @Size(max = 300, message = "Máximo de 300 caracteres.") String notes,
            Long version) {
    }

    public record OrderRef(Long id, String trackingCode, String customerName) {
    }

    public record ExpenseView(Long id, String description, BigDecimal amount, LocalDate expenseDate, Long categoryId,
            String categoryName, OrderRef order, String notes, long version, Instant createdAt) {

        public static ExpenseView from(Expense expense, OrderRef order) {
            return new ExpenseView(expense.getId(), expense.getDescription(), expense.getAmount(),
                    expense.getExpenseDate(), expense.getCategory().getId(), expense.getCategory().getName(), order,
                    expense.getNotes(), expense.getVersion(), expense.getCreatedAt());
        }
    }

    public record CategoryTotal(Long categoryId, String name, BigDecimal total) {
    }

    /**
     * Indicadores do período (BUSINESS_RULES.md §9). Valores em string decimal.
     *
     * @param forecastRevenue        orçamentos aceitos de pedidos confirmados/entregues com retirada no período
     * @param awaitingDepositRevenue orçamentos aceitos ainda sem sinal, com retirada no período (à parte)
     * @param received               pagamentos confirmados no período (sinais + saldos − reembolsos)
     * @param pending                total − recebido dos pedidos confirmados/entregues com retirada no período
     * @param estimatedProfit        previsto − despesas (estimativa)
     * @param cashResult             recebido − despesas
     */
    public record FinanceSummary(LocalDate from, LocalDate to, BigDecimal forecastRevenue, long forecastOrders,
            BigDecimal awaitingDepositRevenue, long awaitingDepositOrders, BigDecimal received,
            BigDecimal receivedDeposits, BigDecimal receivedBalances, BigDecimal refunds, BigDecimal pending,
            BigDecimal expenses, BigDecimal orderExpenses, List<CategoryTotal> expensesByCategory,
            BigDecimal estimatedProfit, BigDecimal cashResult) {
    }
}
