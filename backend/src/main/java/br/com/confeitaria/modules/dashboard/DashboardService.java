package br.com.confeitaria.modules.dashboard;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.config.AppProperties;
import br.com.confeitaria.modules.dashboard.DashboardDtos.ActionCounts;
import br.com.confeitaria.modules.dashboard.DashboardDtos.Dashboard;
import br.com.confeitaria.modules.dashboard.DashboardDtos.ExpiringQuote;
import br.com.confeitaria.modules.dashboard.DashboardDtos.MonthFinance;
import br.com.confeitaria.modules.dashboard.DashboardDtos.MonthOrders;
import br.com.confeitaria.modules.dashboard.DashboardDtos.ProductionCounts;
import br.com.confeitaria.modules.finance.dto.FinanceDtos.FinanceSummary;
import br.com.confeitaria.modules.finance.service.FinanceSummaryService;
import br.com.confeitaria.modules.notification.service.NotificationService;
import br.com.confeitaria.modules.production.ProductionDtos.PickupItem;
import br.com.confeitaria.modules.production.ProductionService;
import br.com.confeitaria.modules.settings.service.SettingsService;

/** Indicadores do painel inicial: o que pede ação, a produção, as próximas retiradas e o mês. */
@Service
public class DashboardService {

    static final int UPCOMING_DAYS = 7;
    static final int UPCOMING_LIMIT = 8;

    private final JdbcTemplate jdbc;
    private final ProductionService production;
    private final FinanceSummaryService finance;
    private final NotificationService notifications;
    private final SettingsService settings;
    private final Clock clock;
    private final AppProperties properties;

    public DashboardService(JdbcTemplate jdbc, ProductionService production, FinanceSummaryService finance,
            NotificationService notifications, SettingsService settings, Clock clock, AppProperties properties) {
        this.jdbc = jdbc;
        this.production = production;
        this.finance = finance;
        this.notifications = notifications;
        this.settings = settings;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, properties.timezone());

        ActionCounts actions = jdbc.queryForObject("""
                select count(*) filter (where commercial_status = 'PENDING_REVIEW'),
                       (select count(*) from payment where status = 'PENDING_REVIEW'),
                       count(*) filter (where commercial_status = 'QUOTE_SENT'),
                       count(*) filter (where commercial_status = 'AWAITING_DEPOSIT'),
                       count(*) filter (where commercial_status = 'CONFIRMED' and pickup_date < ?)
                from cake_order
                """, (rs, n) -> new ActionCounts(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                rs.getLong(5)), Date.valueOf(today));

        ProductionCounts productionCounts = jdbc.queryForObject("""
                select count(*) filter (where production_status = 'NOT_STARTED'),
                       count(*) filter (where production_status = 'IN_PRODUCTION'),
                       count(*) filter (where production_status = 'READY'),
                       count(*) filter (where pickup_date = ?)
                from cake_order where commercial_status = 'CONFIRMED'
                """, (rs, n) -> new ProductionCounts(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)),
                Date.valueOf(today));

        List<PickupItem> upcoming = production.board(UPCOMING_DAYS).orders().stream()
                .filter(item -> !item.overdue()).limit(UPCOMING_LIMIT).toList();

        int warningHours = settings.current().getQuoteExpiryWarningHours();
        List<ExpiringQuote> expiring = jdbc.query("""
                select o.id, o.customer_name, q.valid_until
                from quote q join cake_order o on o.id = q.order_id
                where q.status = 'SENT' and q.valid_until > ? and q.valid_until <= ?
                order by q.valid_until
                limit 10
                """, (rs, n) -> new ExpiringQuote(rs.getLong(1), rs.getString(2),
                rs.getTimestamp(3).toInstant()),
                Timestamp.from(now), Timestamp.from(now.plus(Duration.ofHours(warningHours))));

        FinanceSummary summary = finance.summary(null, null);
        MonthFinance month = new MonthFinance(summary.from(), summary.to(), summary.forecastRevenue(),
                summary.received(), summary.pending(), summary.profit());
        MonthOrders monthOrders = jdbc.queryForObject("""
                select count(*) filter (where commercial_status not in ('REJECTED', 'DECLINED_BY_CLIENT', 'CANCELLED')),
                       count(*) filter (where commercial_status in ('PENDING_REVIEW', 'QUOTE_SENT', 'AWAITING_DEPOSIT')),
                       count(*) filter (where commercial_status in ('CONFIRMED', 'COMPLETED'))
                from cake_order where pickup_date between ? and ?
                """, (rs, n) -> new MonthOrders(rs.getLong(1), rs.getLong(2), rs.getLong(3)),
                Date.valueOf(summary.from()), Date.valueOf(summary.to()));

        return new Dashboard(today, actions, productionCounts, upcoming, expiring, month, monthOrders,
                notifications.unreadCount());
    }
}
