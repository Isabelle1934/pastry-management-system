package br.com.confeitaria.modules.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import br.com.confeitaria.modules.production.ProductionDtos.PickupItem;

/** Contratos do painel inicial. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /** Pedidos que pedem ação da proprietária. */
    public record ActionCounts(long newOrders, long proofsToReview, long awaitingClientResponse,
            long awaitingDeposit, long overduePickups) {
    }

    public record ProductionCounts(long notStarted, long inProduction, long ready, long pickupsToday) {
    }

    public record ExpiringQuote(Long orderId, String customerName, Instant validUntil) {
    }

    /** Valores do mês corrente (mesmas regras do financeiro). */
    public record MonthFinance(LocalDate from, LocalDate to, BigDecimal forecastRevenue, BigDecimal received,
            BigDecimal pending, BigDecimal expenses) {
    }

    public record Dashboard(LocalDate today, ActionCounts actions, ProductionCounts production,
            List<PickupItem> upcomingPickups, List<ExpiringQuote> expiringQuotes, MonthFinance month,
            long unreadNotifications) {
    }
}
