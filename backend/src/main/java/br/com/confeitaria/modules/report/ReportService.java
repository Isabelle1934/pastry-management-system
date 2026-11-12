package br.com.confeitaria.modules.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.config.AppProperties;
import br.com.confeitaria.modules.finance.dto.FinanceDtos.FinanceSummary;
import br.com.confeitaria.modules.finance.service.FinanceSummaryService;
import br.com.confeitaria.modules.finance.service.Periods;
import br.com.confeitaria.modules.order.entity.CommercialStatus;
import br.com.confeitaria.modules.report.ReportDtos.Fulfillment;
import br.com.confeitaria.modules.report.ReportDtos.OrderTotals;
import br.com.confeitaria.modules.report.ReportDtos.Ranked;
import br.com.confeitaria.modules.report.ReportDtos.Report;

/**
 * Relatórios do período pela data agendada (retirada/entrega), as mesmas regras do financeiro:
 * faturamento = orçamentos aceitos de pedidos vendidos; recebido = pagamentos confirmados no período.
 */
@Service
public class ReportService {

    private static final int TOP = 10;
    private static final String SOLD = "o.commercial_status in ('CONFIRMED', 'COMPLETED')";

    private final JdbcTemplate jdbc;
    private final FinanceSummaryService finance;
    private final Clock clock;
    private final AppProperties properties;

    public ReportService(JdbcTemplate jdbc, FinanceSummaryService finance, Clock clock, AppProperties properties) {
        this.jdbc = jdbc;
        this.finance = finance;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Report report(LocalDate from, LocalDate to) {
        Periods.Range range = Periods.resolve(from, to, LocalDate.ofInstant(clock.instant(), properties.timezone()));
        Date start = Date.valueOf(range.from());
        Date end = Date.valueOf(range.to());

        Map<CommercialStatus, Long> statusCounts = new EnumMap<>(CommercialStatus.class);
        for (CommercialStatus status : CommercialStatus.values()) {
            statusCounts.put(status, 0L);
        }
        jdbc.query("""
                select commercial_status, count(*) from cake_order o
                where o.pickup_date between ? and ? group by commercial_status
                """, rs -> {
                    statusCounts.put(CommercialStatus.valueOf(rs.getString(1)), rs.getLong(2));
                }, start, end);

        long orders = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long sold = statusCounts.get(CommercialStatus.CONFIRMED) + statusCounts.get(CommercialStatus.COMPLETED);
        long lost = statusCounts.get(CommercialStatus.REJECTED) + statusCounts.get(CommercialStatus.DECLINED_BY_CLIENT)
                + statusCounts.get(CommercialStatus.CANCELLED);

        BigDecimal revenue = jdbc.queryForObject("select coalesce(sum(q.total), 0) from cake_order o "
                + "join quote q on q.order_id = o.id and q.status = 'ACCEPTED' "
                + "where o.pickup_date between ? and ? and " + SOLD, BigDecimal.class, start, end);
        revenue = revenue == null ? BigDecimal.ZERO : revenue;
        BigDecimal average = sold == 0 ? BigDecimal.ZERO.setScale(2)
                : revenue.divide(BigDecimal.valueOf(sold), 2, RoundingMode.HALF_UP);
        FinanceSummary summary = finance.summary(range.from(), range.to());

        List<Ranked> topProducts = jdbc.query("""
                select i.name_snapshot, i.category_snapshot, sum(i.quantity) as qty, count(distinct o.id) as orders
                from order_item i join cake_order o on o.id = i.order_id
                where o.pickup_date between ? and ? and %s
                group by i.name_snapshot, i.category_snapshot
                order by qty desc, i.name_snapshot limit %d
                """.formatted(SOLD, TOP), (rs, n) -> new Ranked(rs.getString(1), rs.getString(2), rs.getLong(3),
                rs.getLong(4)), start, end);
        List<Ranked> categories = jdbc.query("""
                select i.category_snapshot, sum(i.quantity) as qty, count(distinct o.id) as orders
                from order_item i join cake_order o on o.id = i.order_id
                where o.pickup_date between ? and ? and %s
                group by i.category_snapshot order by qty desc, i.category_snapshot
                """.formatted(SOLD), (rs, n) -> new Ranked(rs.getString(1), null, rs.getLong(2), rs.getLong(3)),
                start, end);
        List<Ranked> sizes = jdbc.query("""
                select o.size_name_snapshot, count(*) from cake_order o
                where o.pickup_date between ? and ? and o.custom_cake and %s
                group by o.size_name_snapshot order by count(*) desc, o.size_name_snapshot limit %d
                """.formatted(SOLD, TOP), (rs, n) -> new Ranked(rs.getString(1), null, rs.getLong(2), rs.getLong(2)),
                start, end);
        List<Ranked> flavors = optionRanking("order_flavor", start, end);
        List<Ranked> fillings = optionRanking("order_filling", start, end);

        long[] extras = jdbc.queryForObject("""
                select count(*) filter (where o.custom_cake), count(*) filter (where o.topper_requested),
                       count(*) filter (where o.fulfillment_type = 'PICKUP'),
                       count(*) filter (where o.fulfillment_type = 'DELIVERY')
                from cake_order o where o.pickup_date between ? and ? and %s
                """.formatted(SOLD), (rs, n) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)},
                start, end);

        return new Report(range.from(), range.to(), statusCounts,
                new OrderTotals(orders, sold, lost, revenue.setScale(2), average, summary.received()), topProducts,
                categories, sizes, flavors, fillings, extras[0], extras[1], new Fulfillment(extras[2], extras[3]));
    }

    private List<Ranked> optionRanking(String table, Date start, Date end) {
        return jdbc.query("""
                select c.name_snapshot, count(*) from %s c join cake_order o on o.id = c.order_id
                where o.pickup_date between ? and ? and %s
                group by c.name_snapshot order by count(*) desc, c.name_snapshot limit %d
                """.formatted(table, SOLD, TOP), (rs, n) -> new Ranked(rs.getString(1), null, rs.getLong(2),
                rs.getLong(2)), start, end);
    }
}
