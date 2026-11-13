package br.com.confeitaria.modules.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Contrato do resumo financeiro do período. */
public final class FinanceDtos {

    private FinanceDtos() {
    }

    /**
     * Indicadores do período (BUSINESS_RULES.md §9). Valores em string decimal. Só pagamentos confirmados
     * pela proprietária entram como recebidos.
     *
     * @param forecastRevenue        faturamento previsto: orçamentos aceitos de pedidos confirmados/concluídos
     *                               com data agendada no período
     * @param awaitingDepositRevenue orçamentos aceitos ainda sem sinal, com data no período (à parte)
     * @param received               pagamentos confirmados no período (sinais + saldos − reembolsos)
     * @param pending                a receber: total − recebido dos pedidos confirmados/concluídos do período
     * @param profit                 lucro: recebido líquido no período. Sem cadastro de custos, é igual ao
     *                               recebido (pendência P18)
     */
    public record FinanceSummary(LocalDate from, LocalDate to, BigDecimal forecastRevenue, long forecastOrders,
            BigDecimal awaitingDepositRevenue, long awaitingDepositOrders, BigDecimal received,
            BigDecimal receivedDeposits, BigDecimal receivedBalances, BigDecimal refunds, BigDecimal pending,
            BigDecimal profit) {
    }
}
