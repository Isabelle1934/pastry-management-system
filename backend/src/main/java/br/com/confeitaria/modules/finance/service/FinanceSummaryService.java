package br.com.confeitaria.modules.finance.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.config.AppProperties;
import br.com.confeitaria.modules.finance.dto.FinanceDtos.FinanceSummary;

/**
 * Indicadores financeiros do período (BUSINESS_RULES.md §9). Faturamento e pendências seguem a
 * data agendada; recebimentos, a data de confirmação do pagamento.
 */
@Service
public class FinanceSummaryService {

    /** Total do orçamento aceito e valor já recebido (pagamentos confirmados, reembolsos negativos) por pedido. */
    private static final String ORDER_TOTALS = """
            select o.id, o.commercial_status, q.total,
                   coalesce((select sum(case when p.kind = 'REFUND' then -p.amount else p.amount end)
                             from payment p where p.order_id = o.id and p.status = 'CONFIRMED'), 0) as received
            from cake_order o
            join quote q on q.order_id = o.id and q.status = 'ACCEPTED'
            where o.pickup_date between ? and ?
              and o.commercial_status in ('CONFIRMED', 'COMPLETED', 'AWAITING_DEPOSIT')
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AppProperties properties;

    public FinanceSummaryService(JdbcTemplate jdbc, Clock clock, AppProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public FinanceSummary summary(LocalDate from, LocalDate to) {
        ZoneId zone = properties.timezone();
        Periods.Range range = Periods.resolve(from, to, LocalDate.ofInstant(clock.instant(), zone));
        Date start = Date.valueOf(range.from());
        Date end = Date.valueOf(range.to());

        BigDecimal[] forecast = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        long[] counts = {0, 0};
        jdbc.query(ORDER_TOTALS, rs -> {
            BigDecimal total = rs.getBigDecimal("total");
            if ("AWAITING_DEPOSIT".equals(rs.getString("commercial_status"))) {
                forecast[1] = forecast[1].add(total);
                counts[1]++;
            } else {
                forecast[0] = forecast[0].add(total);
                forecast[2] = forecast[2].add(total.subtract(rs.getBigDecimal("received")).max(BigDecimal.ZERO));
                counts[0]++;
            }
        }, start, end);

        Timestamp receivedFrom = Timestamp.from(range.from().atStartOfDay(zone).toInstant());
        Timestamp receivedTo = Timestamp.from(range.to().plusDays(1).atStartOfDay(zone).toInstant());
        BigDecimal[] received = jdbc.queryForObject("""
                select coalesce(sum(amount) filter (where kind = 'DEPOSIT'), 0),
                       coalesce(sum(amount) filter (where kind = 'BALANCE'), 0),
                       coalesce(sum(amount) filter (where kind = 'REFUND'), 0)
                from payment
                where status = 'CONFIRMED' and reviewed_at >= ? and reviewed_at < ?
                """, (rs, n) -> new BigDecimal[] {rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3)},
                receivedFrom, receivedTo);

        BigDecimal receivedTotal = received[0].add(received[1]).subtract(received[2]);
        return new FinanceSummary(range.from(), range.to(), money(forecast[0]), counts[0], money(forecast[1]),
                counts[1], money(receivedTotal), money(received[0]), money(received[1]), money(received[2]),
                money(forecast[2]), money(receivedTotal));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2);
    }
}
