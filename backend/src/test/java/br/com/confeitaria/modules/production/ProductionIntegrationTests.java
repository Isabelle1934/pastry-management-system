package br.com.confeitaria.modules.production;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.login;
import static br.com.confeitaria.support.AuthTestSupport.uniqueIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.confeitaria.modules.auth.service.AuthRateLimiter;
import br.com.confeitaria.modules.tracking.TrackingAccess;
import br.com.confeitaria.support.AuthTestSupport;
import br.com.confeitaria.support.IntegrationTest;
import br.com.confeitaria.support.MutableClock;
import br.com.confeitaria.support.OrderFixtures;
import br.com.confeitaria.support.OrderFixtures.Catalog;
import br.com.confeitaria.support.OrderFixtures.CreatedOrder;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Pedidos de teste: orçamento de R$ 165,00 (sinal R$ 82,50), retirada terça 03/11 às 14:00; hoje é 02/11. */
@IntegrationTest
class ProductionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderFixtures fixtures;

    @Autowired
    private AuthTestSupport auth;

    @Autowired
    private AuthRateLimiter authLimiter;

    @Autowired
    private TrackingAccess trackingAccess;

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
        trackingAccess.clearLimits();
        catalog = fixtures.prepare();
        admin = login(mockMvc, auth.createUser().getEmail(), PASSWORD);
    }

    private ResultActions adminPost(String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Pedido com o sinal registrado pela proprietária → confirmado, vaga reservada. */
    private CreatedOrder confirmedOrder() throws Exception {
        CreatedOrder order = fixtures.acceptedOrder(catalog);
        registerPayment(order.id(), "DEPOSIT", "82.50");
        assertThat(commercialStatus(order.id())).isEqualTo("CONFIRMED");
        return order;
    }

    private void registerPayment(long orderId, String kind, String amount) throws Exception {
        adminPost("/api/admin/orders/" + orderId + "/payments",
                "{\"kind\":\"" + kind + "\",\"method\":\"PIX\",\"amount\":\"" + amount + "\"}")
                .andExpect(status().isOk());
    }

    private long version(long orderId) throws Exception {
        String body = mockMvc.perform(get("/api/admin/orders/" + orderId).cookie(admin))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("version").asLong();
    }

    private ResultActions change(long orderId, String status, String extra) throws Exception {
        return adminPost("/api/admin/orders/" + orderId + "/production",
                "{\"status\":\"" + status + "\",\"version\":" + version(orderId) + (extra == null ? "" : "," + extra) + "}");
    }

    private String commercialStatus(long orderId) {
        return jdbc.queryForObject("select commercial_status from cake_order where id = ?", String.class, orderId);
    }

    private List<String> historyEvents(long orderId) {
        return jdbc.queryForList("select event_type from order_history where order_id = ? order by id", String.class,
                orderId);
    }

    private JsonNode boardEntry(String query, long orderId) throws Exception {
        String body = mockMvc.perform(get("/api/admin/production" + query).cookie(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode item : json.readTree(body).get("orders")) {
            if (item.get("id").asLong() == orderId) {
                return item;
            }
        }
        return null;
    }

    @Test
    void productionAdvancesStepByStepAndDeliveryAfterFullPaymentCompletesTheOrder() throws Exception {
        CreatedOrder order = confirmedOrder();

        change(order.id(), "IN_PRODUCTION", "\"note\":\"Massa assada\"").andExpect(status().isOk())
                .andExpect(jsonPath("$.productionStatus").value("IN_PRODUCTION"))
                .andExpect(jsonPath("$.commercialStatus").value("CONFIRMED"));
        change(order.id(), "READY", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.productionStatus").value("READY"));

        change(order.id(), "DELIVERED", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BALANCE_DUE"));
        assertThat(commercialStatus(order.id())).isEqualTo("CONFIRMED");

        registerPayment(order.id(), "BALANCE", "82.50");
        change(order.id(), "DELIVERED", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.productionStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.commercialStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.slotReserved").value(true));

        assertThat(historyEvents(order.id())).containsSubsequence("CONFIRMED", "PRODUCTION_IN_PRODUCTION",
                "PRODUCTION_READY", "PRODUCTION_DELIVERED");
        assertThat(jdbc.queryForObject("select details->>'note' from order_history where order_id = ? "
                + "and event_type = 'PRODUCTION_IN_PRODUCTION'", String.class, order.id())).isEqualTo("Massa assada");

        change(order.id(), "READY", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CONFIRMED"));
        adminPost("/api/admin/orders/" + order.id() + "/cancel",
                "{\"reason\":\"teste\",\"version\":" + version(order.id()) + "}")
                .andExpect(status().isConflict());
    }

    @Test
    void deliveryWithBalanceDueIsAllowedOnlyWithExplicitConfirmationAndIsRecorded() throws Exception {
        CreatedOrder order = confirmedOrder();
        change(order.id(), "IN_PRODUCTION", null).andExpect(status().isOk());
        change(order.id(), "READY", null).andExpect(status().isOk());

        change(order.id(), "DELIVERED", "\"confirmBalanceDue\":false").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BALANCE_DUE"));
        change(order.id(), "DELIVERED", "\"confirmBalanceDue\":true").andExpect(status().isOk())
                .andExpect(jsonPath("$.commercialStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.payments.balanceDue").value("82.50"));

        assertThat(jdbc.queryForObject("select details->>'balanceDue' from order_history where order_id = ? "
                + "and event_type = 'PRODUCTION_DELIVERED'", String.class, order.id())).isEqualTo("82.50");
        // O saldo ainda pode ser registrado depois da entrega.
        registerPayment(order.id(), "BALANCE", "82.50");
        assertThat(jdbc.queryForObject("select payment_status from cake_order where id = ?", String.class, order.id()))
                .isEqualTo("PAID");
    }

    @Test
    void onlyConfirmedOrdersEnterProductionAndStepsCannotBeSkipped() throws Exception {
        CreatedOrder awaiting = fixtures.acceptedOrder(catalog);
        change(awaiting.id(), "IN_PRODUCTION", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CONFIRMED"));

        CreatedOrder order = confirmedOrder();
        change(order.id(), "READY", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PRODUCTION_TRANSITION"));
        change(order.id(), "DELIVERED", "\"confirmBalanceDue\":true").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PRODUCTION_TRANSITION"));
        change(order.id(), "NOT_STARTED", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PRODUCTION_TRANSITION"));

        // Voltar um passo corrige engano.
        change(order.id(), "IN_PRODUCTION", null).andExpect(status().isOk());
        change(order.id(), "READY", null).andExpect(status().isOk());
        change(order.id(), "IN_PRODUCTION", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.productionStatus").value("IN_PRODUCTION"));
        assertThat(historyEvents(order.id())).filteredOn(e -> e.startsWith("PRODUCTION_")).hasSize(3);
    }

    @Test
    void staleVersionAndInvalidBodyAreRejected() throws Exception {
        CreatedOrder order = confirmedOrder();
        long stale = version(order.id());
        change(order.id(), "IN_PRODUCTION", null).andExpect(status().isOk());

        adminPost("/api/admin/orders/" + order.id() + "/production",
                "{\"status\":\"READY\",\"version\":" + stale + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        adminPost("/api/admin/orders/" + order.id() + "/production", "{\"version\":" + version(order.id()) + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        adminPost("/api/admin/orders/" + order.id() + "/production",
                "{\"status\":\"READY\",\"version\":" + version(order.id()) + ",\"note\":\"" + "x".repeat(301) + "\"}")
                .andExpect(status().isBadRequest());
        adminPost("/api/admin/orders/999999/production", "{\"status\":\"READY\",\"version\":0}")
                .andExpect(status().isNotFound());
    }

    @Test
    void pickupBoardListsConfirmedOrdersWithBalanceAndFlagsOverdueOnes() throws Exception {
        CreatedOrder confirmed = confirmedOrder();
        CreatedOrder awaiting = fixtures.acceptedOrder(catalog);

        JsonNode entry = boardEntry("", confirmed.id());
        assertThat(entry).isNotNull();
        assertThat(entry.get("pickupDate").asText()).isEqualTo("2026-11-03");
        assertThat(entry.get("pickupTime").asText()).isEqualTo("14:00");
        assertThat(entry.get("balanceDue").asText()).isEqualTo("82.50");
        assertThat(entry.get("total").asText()).isEqualTo("165.00");
        assertThat(entry.get("productionStatus").asText()).isEqualTo("NOT_STARTED");
        assertThat(entry.get("overdue").asBoolean()).isFalse();
        assertThat(entry.get("customerWhatsapp").asText()).isEqualTo("(11) 98765-4321");
        assertThat(boardEntry("", awaiting.id())).isNull();

        // Hoje (02/11) com 0 dias: a retirada de 03/11 fica fora.
        assertThat(boardEntry("?days=0", confirmed.id())).isNull();

        // Depois da data de retirada, sem entrega: continua na lista como atrasado.
        clock.advance(Duration.ofDays(3));
        assertThat(boardEntry("?days=0", confirmed.id()).get("overdue").asBoolean()).isTrue();

        change(confirmed.id(), "IN_PRODUCTION", null).andExpect(status().isOk());
        change(confirmed.id(), "READY", null).andExpect(status().isOk());
        change(confirmed.id(), "DELIVERED", "\"confirmBalanceDue\":true").andExpect(status().isOk());
        assertThat(boardEntry("", confirmed.id())).isNull();
    }

    @Test
    void todayFollowsTheBakeryTimezoneNotUtc() throws Exception {
        CreatedOrder confirmed = confirmedOrder();
        // 03/11 01:00 UTC = 02/11 22:00 em São Paulo: a retirada de 03/11 ainda não é "hoje".
        clock.set(java.time.Instant.parse("2026-11-03T01:00:00Z"));
        String body = mockMvc.perform(get("/api/admin/production?days=0").cookie(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).get("today").asText()).isEqualTo("2026-11-02");
        assertThat(boardEntry("?days=0", confirmed.id())).isNull();
    }

    @Test
    void productionRequiresAdminAndIsVisibleToTheClient() throws Exception {
        CreatedOrder order = confirmedOrder();
        mockMvc.perform(get("/api/admin/production")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/orders/" + order.id() + "/production").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PRODUCTION\",\"version\":0}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/orders/" + order.id() + "/production").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PRODUCTION\",\"version\":0}"))
                .andExpect(status().isForbidden());

        change(order.id(), "IN_PRODUCTION", null).andExpect(status().isOk());
        Cookie session = mockMvc.perform(post("/api/tracking/verify").with(csrf()).with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingCode\":\"" + order.trackingCode() + "\",\"whatsappLast4\":\"4321\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        mockMvc.perform(get("/api/tracking/order").cookie(session))
                .andExpect(jsonPath("$.productionStatus").value("IN_PRODUCTION"));
    }
}
