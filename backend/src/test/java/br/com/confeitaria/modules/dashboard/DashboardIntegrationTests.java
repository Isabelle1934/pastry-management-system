package br.com.confeitaria.modules.dashboard;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import br.com.confeitaria.modules.auth.service.AuthRateLimiter;
import br.com.confeitaria.modules.quote.service.QuoteService;
import br.com.confeitaria.support.AuthTestSupport;
import br.com.confeitaria.support.IntegrationTest;
import br.com.confeitaria.support.MutableClock;
import br.com.confeitaria.support.OrderFixtures;
import br.com.confeitaria.support.OrderFixtures.Catalog;
import br.com.confeitaria.support.OrderFixtures.CreatedOrder;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Banco compartilhado entre as classes de teste: contagens são verificadas pela diferença antes/depois.
 * Hoje é 02/11/2026 (12:00 UTC); pedidos de teste retiram em 03/11 às 14:00; orçamento vale 48 h, aviso 12 h.
 */
@IntegrationTest
class DashboardIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderFixtures fixtures;

    @Autowired
    private AuthTestSupport auth;

    @Autowired
    private AuthRateLimiter authLimiter;

    @Autowired
    private QuoteService quotes;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    private Catalog catalog;
    private Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        authLimiter.clear();
        catalog = fixtures.prepare();
        admin = login(mockMvc, auth.createUser().getEmail(), PASSWORD);
    }

    private JsonNode dashboard() throws Exception {
        return json.readTree(mockMvc.perform(get("/api/admin/dashboard").cookie(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static long delta(JsonNode before, JsonNode after, String group, String field) {
        return after.get(group).get(field).asLong() - before.get(group).get(field).asLong();
    }

    private static boolean contains(JsonNode list, String idField, long id) {
        for (JsonNode item : list) {
            if (item.get(idField).asLong() == id) {
                return true;
            }
        }
        return false;
    }

    private void registerDeposit(long orderId) throws Exception {
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/payments").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"DEPOSIT\",\"method\":\"PIX\",\"amount\":\"82.50\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void countersFollowTheOrderThroughItsLifecycle() throws Exception {
        JsonNode start = dashboard();
        assertThat(start.get("today").asText()).isEqualTo("2026-11-02");

        CreatedOrder order = fixtures.create(catalog);
        JsonNode created = dashboard();
        assertThat(delta(start, created, "actions", "newOrders")).isEqualTo(1);

        long quoteId = fixtures.sendQuote(order.id());
        JsonNode sent = dashboard();
        assertThat(delta(start, sent, "actions", "newOrders")).isZero();
        assertThat(delta(start, sent, "actions", "awaitingClientResponse")).isEqualTo(1);
        assertThat(contains(sent.get("expiringQuotes"), "orderId", order.id())).isFalse();

        quotes.accept(order.id(), quoteId);
        JsonNode accepted = dashboard();
        assertThat(delta(start, accepted, "actions", "awaitingClientResponse")).isZero();
        assertThat(delta(start, accepted, "actions", "awaitingDeposit")).isEqualTo(1);

        registerDeposit(order.id());
        JsonNode confirmed = dashboard();
        assertThat(delta(start, confirmed, "actions", "awaitingDeposit")).isZero();
        assertThat(delta(start, confirmed, "production", "notStarted")).isEqualTo(1);
        assertThat(contains(confirmed.get("upcomingPickups"), "id", order.id())).isTrue();
        assertThat(new BigDecimal(confirmed.get("month").get("received").asText())
                .subtract(new BigDecimal(start.get("month").get("received").asText())))
                .isEqualByComparingTo("82.50");

        long version = jdbc.queryForObject("select version from cake_order where id = ?", Long.class, order.id());
        mockMvc.perform(post("/api/admin/orders/" + order.id() + "/production").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PRODUCTION\",\"version\":" + version + "}"))
                .andExpect(status().isOk());
        JsonNode producing = dashboard();
        assertThat(delta(start, producing, "production", "notStarted")).isZero();
        assertThat(delta(start, producing, "production", "inProduction")).isEqualTo(1);
    }

    @Test
    void quotesNearExpiryAndPickupsTodayOrOverdueAreHighlighted() throws Exception {
        CreatedOrder pending = fixtures.create(catalog);
        fixtures.sendQuote(pending.id());
        CreatedOrder confirmed = fixtures.acceptedOrder(catalog);
        registerDeposit(confirmed.id());

        // Orçamento enviado às 12:00 de 02/11 vale até 12:00 de 04/11; aviso nas últimas 12 h.
        clock.advance(Duration.ofHours(40));
        JsonNode near = dashboard();
        assertThat(contains(near.get("expiringQuotes"), "orderId", pending.id())).isTrue();
        // 04/11 04:00 UTC = 04/11 01:00 em São Paulo: a retirada de 03/11 já passou.
        assertThat(near.get("today").asText()).isEqualTo("2026-11-04");
        assertThat(near.get("actions").get("overduePickups").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(contains(near.get("upcomingPickups"), "id", confirmed.id())).isFalse();

        clock.set(OrderFixtures.NOW.plus(Duration.ofHours(18))); // 03/11 03:00 São Paulo
        JsonNode pickupDay = dashboard();
        assertThat(pickupDay.get("today").asText()).isEqualTo("2026-11-03");
        assertThat(pickupDay.get("production").get("pickupsToday").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(contains(pickupDay.get("expiringQuotes"), "orderId", pending.id())).isFalse();
    }

    @Test
    void dashboardRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
    }
}
