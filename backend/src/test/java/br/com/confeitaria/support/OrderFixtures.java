package br.com.confeitaria.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.confeitaria.modules.order.dto.PublicOrderDtos.OrderRequest;
import br.com.confeitaria.modules.order.service.OrderCreationService;
import br.com.confeitaria.modules.order.service.OrderRateLimiter;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.DraftRequest;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.ItemRequest;
import br.com.confeitaria.modules.quote.service.QuoteService;
import br.com.confeitaria.modules.schedule.ScheduleTestSupport;

/**
 * Prepara catálogo, agenda e pedidos reais (pelo mesmo serviço do formulário público).
 * "Agora" = segunda 02/11/2026 09:00 em São Paulo; horários às terças e sextas 14:00.
 */
@TestComponent
public class OrderFixtures {

    public static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    public static final LocalDate TUESDAY = LocalDate.of(2026, 11, 3);
    public static final LocalDate FRIDAY = LocalDate.of(2026, 11, 6);
    public static final LocalTime TWO_PM = LocalTime.of(14, 0);
    public static final String WHATSAPP = "(11) 98765-4321";

    public record Catalog(long sizeId, long flavorId, long otherFlavorId, long fillingId) {
    }

    public record CreatedOrder(long id, String trackingCode) {
    }

    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final OrderCreationService creation;
    private final OrderRateLimiter rateLimiter;
    private final QuoteService quotes;

    public OrderFixtures(JdbcTemplate jdbc, MutableClock clock, OrderCreationService creation,
            OrderRateLimiter rateLimiter, QuoteService quotes) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.creation = creation;
        this.rateLimiter = rateLimiter;
        this.quotes = quotes;
    }

    /** Reinicia relógio e agenda e cria um catálogo novo (nomes únicos). */
    public Catalog prepare() {
        clock.set(NOW);
        rateLimiter.clear();
        jdbc.update("update bakery_settings set deposit_deadline_hours = 48, quote_expiry_warning_hours = 12");
        ScheduleTestSupport schedule = new ScheduleTestSupport(jdbc);
        schedule.reset(24, 90);
        schedule.slot(2, "14:00", 3, true);
        schedule.slot(5, "14:00", 3, true);
        String suffix = " " + UUID.randomUUID().toString().substring(0, 8);
        long size = id("insert into cake_size (name, base_price, max_flavors, max_fillings, created_at, updated_at) "
                + "values (?, 150, 2, 1, now(), now()) returning id", "Médio" + suffix);
        long flavor = id("insert into cake_flavor (name, created_at, updated_at) values (?, now(), now()) returning id",
                "Chocolate" + suffix);
        long other = id("insert into cake_flavor (name, created_at, updated_at) values (?, now(), now()) returning id",
                "Baunilha" + suffix);
        long filling = id("insert into cake_filling (name, created_at, updated_at) values (?, now(), now()) returning id",
                "Brigadeiro" + suffix);
        return new Catalog(size, flavor, other, filling);
    }

    public OrderRequest request(Catalog catalog, LocalDate date) {
        return new OrderRequest("Ana Souza", WHATSAPP, date, TWO_PM, catalog.sizeId(), List.of(catalog.flavorId()),
                List.of(catalog.fillingId()), "Flores", "Parabéns!", null, false, null, null, null, null);
    }

    public CreatedOrder create(Catalog catalog) {
        String code = creation.create(request(catalog, TUESDAY), List.of(), null, "192.0.2." + (1 + (int) (Math.random() * 200)))
                .trackingCode();
        Long orderId = jdbc.queryForObject("select id from cake_order where tracking_code = ?", Long.class,
                code.replace("-", ""));
        return new CreatedOrder(orderId, code);
    }

    /** Pedido com orçamento de R$ 165,00 (sinal R$ 82,50) enviado e aceito → aguardando o sinal. */
    public CreatedOrder acceptedOrder(Catalog catalog) {
        CreatedOrder order = create(catalog);
        long quoteId = sendQuote(order.id());
        quotes.accept(order.id(), quoteId);
        return order;
    }

    /** Envia um orçamento de R$ 120 + R$ 25 + R$ 20 = R$ 165,00 e devolve o id. */
    public long sendQuote(long orderId) {
        quotes.saveDraft(orderId, new DraftRequest(List.of(
                new ItemRequest("Bolo personalizado", null, BigDecimal.ONE, new BigDecimal("120.00")),
                new ItemRequest("Decoração especial", null, BigDecimal.ONE, new BigDecimal("25.00")),
                new ItemRequest("Topo personalizado", null, BigDecimal.ONE, new BigDecimal("20.00"))), null, 48));
        return quotes.send(orderId, 1L).id();
    }

    private long id(String sql, String name) {
        Long value = jdbc.queryForObject(sql, Long.class, name);
        return value == null ? -1 : value;
    }
}
