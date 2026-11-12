package br.com.confeitaria.modules.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import br.com.confeitaria.modules.order.entity.CommercialStatus;

/** Contratos dos relatórios gerenciais. */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** Pedidos do período (data agendada). {@code sold} = confirmados ou concluídos. */
    public record OrderTotals(long orders, long sold, long lost, BigDecimal soldRevenue, BigDecimal averageTicket,
            BigDecimal received) {
    }

    public record Ranked(String name, String group, long quantity, long orders) {
    }

    public record Fulfillment(long pickup, long delivery) {
    }

    /**
     * Itens, bolos, massas e recheios consideram só pedidos vendidos (confirmados ou concluídos);
     * os nomes vêm da cópia guardada no pedido, então produtos renomeados ou desativados continuam corretos.
     */
    public record Report(LocalDate from, LocalDate to, Map<CommercialStatus, Long> statusCounts, OrderTotals totals,
            List<Ranked> topProducts, List<Ranked> categories, List<Ranked> cakeSizes, List<Ranked> flavors,
            List<Ranked> fillings, long customCakes, long toppers, Fulfillment fulfillment) {
    }
}
