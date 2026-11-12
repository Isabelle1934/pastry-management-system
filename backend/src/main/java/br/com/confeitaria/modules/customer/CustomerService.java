package br.com.confeitaria.modules.customer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.validation.WhatsAppNumber;
import br.com.confeitaria.modules.customer.CustomerDtos.CustomerSummary;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.PageResponse;

/**
 * Clientes, a partir dos pedidos já registrados: cada WhatsApp é uma cliente (o mesmo critério do
 * acompanhamento). Não há cadastro separado, então nenhum dado pessoal novo é coletado ou duplicado.
 */
@Service
public class CustomerService {

    private static final String BASE = """
            with c as (
                select o.customer_whatsapp as whatsapp,
                       (array_agg(o.customer_name order by o.created_at desc))[1] as name,
                       count(*) as orders,
                       count(*) filter (where o.commercial_status in ('CONFIRMED', 'COMPLETED')) as confirmed,
                       count(*) filter (where o.commercial_status = 'COMPLETED') as completed,
                       min(o.created_at) as first_order_at,
                       max(o.created_at) as last_order_at,
                       max(o.pickup_date) as last_pickup_date
                from cake_order o
                group by o.customer_whatsapp
            )
            """;

    private static final String FILTER = """
            where (? = '' or lower(c.name) like ? escape '\\' or c.whatsapp like ?)
            """;

    private final JdbcTemplate jdbc;

    public CustomerService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Busca por nome ou dígitos do WhatsApp; mais recentes primeiro. */
    @Transactional(readOnly = true)
    public PageResponse<CustomerSummary> list(String query, int page, int size) {
        String text = query == null ? "" : query.strip();
        String nameLike = "%" + escape(text.toLowerCase(Locale.ROOT)) + "%";
        String digits = text.replaceAll("\\D", "");
        String phoneLike = digits.length() >= 4 ? "%" + digits + "%" : "-";
        int pageSize = Math.clamp(size, 1, 100);
        int pageNumber = Math.max(page, 0);

        Long total = jdbc.queryForObject(BASE + "select count(*) from c " + FILTER, Long.class, text, nameLike,
                phoneLike);
        List<CustomerSummary> content = jdbc.query(BASE + """
                select c.*, coalesce(p.paid, 0) as paid
                from c
                left join (
                    select o.customer_whatsapp, sum(case when p.kind = 'REFUND' then -p.amount else p.amount end) as paid
                    from payment p join cake_order o on o.id = p.order_id
                    where p.status = 'CONFIRMED'
                    group by o.customer_whatsapp
                ) p on p.customer_whatsapp = c.whatsapp
                """ + FILTER + """
                order by c.last_order_at desc
                limit ? offset ?
                """, (rs, n) -> {
                    String whatsapp = rs.getString("whatsapp");
                    Timestamp first = rs.getTimestamp("first_order_at");
                    Timestamp last = rs.getTimestamp("last_order_at");
                    BigDecimal paid = rs.getBigDecimal("paid");
                    return new CustomerSummary(rs.getString("name"), WhatsAppNumber.format(whatsapp),
                            whatsapp.substring(1), "https://wa.me/" + whatsapp.substring(1), rs.getLong("orders"),
                            rs.getLong("confirmed"), rs.getLong("completed"), paid.setScale(2),
                            first.toInstant(), last.toInstant(), rs.getDate("last_pickup_date").toLocalDate());
                }, text, nameLike, phoneLike, pageSize, (long) pageNumber * pageSize);

        long totalElements = total == null ? 0 : total;
        return new PageResponse<>(content, pageNumber, pageSize, totalElements,
                (int) Math.ceil(totalElements / (double) pageSize));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
