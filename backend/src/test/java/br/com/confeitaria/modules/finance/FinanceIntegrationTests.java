package br.com.confeitaria.modules.finance;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.confeitaria.modules.auth.service.AuthRateLimiter;
import br.com.confeitaria.support.AuthTestSupport;
import br.com.confeitaria.support.IntegrationTest;
import br.com.confeitaria.support.OrderFixtures;
import br.com.confeitaria.support.OrderFixtures.Catalog;
import br.com.confeitaria.support.OrderFixtures.CreatedOrder;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * O banco de testes é compartilhado entre as classes: indicadores de pedidos e pagamentos são
 * verificados pela diferença antes/depois.
 * Pedidos de teste: orçamento de R$ 165,00 (sinal R$ 82,50), retirada 03/11/2026; hoje é 02/11/2026.
 */
@IntegrationTest
class FinanceIntegrationTests {

    private static final String NOVEMBER = "?from=2026-11-01&to=2026-11-30";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderFixtures fixtures;

    @Autowired
    private AuthTestSupport auth;

    @Autowired
    private AuthRateLimiter authLimiter;

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

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String body) throws Exception {
        return mockMvc.perform(request.with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode summary(String query) throws Exception {
        return read(mockMvc.perform(get("/api/admin/finance/summary" + query).cookie(admin))
                .andExpect(status().isOk()));
    }

    private static BigDecimal money(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    private static BigDecimal delta(JsonNode before, JsonNode after, String field) {
        return money(after, field).subtract(money(before, field));
    }

    private void registerPayment(long orderId, String kind, String amount) throws Exception {
        send(post("/api/admin/orders/" + orderId + "/payments"),
                "{\"kind\":\"" + kind + "\",\"method\":\"CASH\",\"amount\":\"" + amount + "\"}")
                .andExpect(status().isOk());
    }

    @Test
    void forecastFollowsConfirmedOrdersWhileReceivedFollowsConfirmedPayments() throws Exception {
        JsonNode start = summary(NOVEMBER);

        CreatedOrder order = fixtures.acceptedOrder(catalog);
        JsonNode awaiting = summary(NOVEMBER);
        assertThat(delta(start, awaiting, "awaitingDepositRevenue")).isEqualByComparingTo("165.00");
        assertThat(awaiting.get("awaitingDepositOrders").asLong() - start.get("awaitingDepositOrders").asLong())
                .isEqualTo(1);
        assertThat(delta(start, awaiting, "forecastRevenue")).isZero();
        assertThat(delta(start, awaiting, "received")).isZero();

        registerPayment(order.id(), "DEPOSIT", "82.50");
        JsonNode confirmed = summary(NOVEMBER);
        assertThat(delta(start, confirmed, "awaitingDepositRevenue")).isZero();
        assertThat(delta(start, confirmed, "forecastRevenue")).isEqualByComparingTo("165.00");
        assertThat(confirmed.get("forecastOrders").asLong() - start.get("forecastOrders").asLong()).isEqualTo(1);
        assertThat(delta(start, confirmed, "received")).isEqualByComparingTo("82.50");
        assertThat(delta(start, confirmed, "receivedDeposits")).isEqualByComparingTo("82.50");
        assertThat(delta(start, confirmed, "pending")).isEqualByComparingTo("82.50");

        registerPayment(order.id(), "BALANCE", "82.50");
        registerPayment(order.id(), "REFUND", "10.00");
        JsonNode paid = summary(NOVEMBER);
        assertThat(delta(start, paid, "forecastRevenue")).isEqualByComparingTo("165.00");
        assertThat(delta(start, paid, "received")).isEqualByComparingTo("155.00");
        assertThat(delta(start, paid, "receivedBalances")).isEqualByComparingTo("82.50");
        assertThat(delta(start, paid, "refunds")).isEqualByComparingTo("10.00");
        assertThat(delta(start, paid, "pending")).isEqualByComparingTo("10.00");
        assertThat(delta(start, paid, "profit")).isEqualByComparingTo("155.00");

        // Pedidos fora do período (retirada em dezembro) não entram.
        JsonNode december = summary("?from=2026-12-01&to=2026-12-31");
        assertThat(money(december, "forecastRevenue")).isZero();
    }

    @Test
    void cancelledOrderLeavesTheForecastButItsDepositStaysAsReceived() throws Exception {
        JsonNode start = summary(NOVEMBER);
        CreatedOrder order = fixtures.acceptedOrder(catalog);
        registerPayment(order.id(), "DEPOSIT", "82.50");
        long version = jdbc.queryForObject("select version from cake_order where id = ?", Long.class, order.id());
        send(post("/api/admin/orders/" + order.id() + "/cancel"), "{\"reason\":\"Cliente desistiu\",\"version\":"
                + version + "}").andExpect(status().isOk());

        JsonNode after = summary(NOVEMBER);
        assertThat(delta(start, after, "forecastRevenue")).isZero();
        assertThat(delta(start, after, "pending")).isZero();
        assertThat(delta(start, after, "received")).isEqualByComparingTo("82.50");
    }

    @Test
    void summaryValidatesThePeriodAndExpensesAreNoLongerExposed() throws Exception {
        mockMvc.perform(get("/api/admin/finance/summary?from=2026-01-01&to=2027-01-02").cookie(admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/finance/summary?from=2026-02-01&to=2026-01-01").cookie(admin))
                .andExpect(status().isBadRequest());
        // O módulo de despesas foi retirado do painel; os registros antigos continuam no banco.
        mockMvc.perform(get("/api/admin/finance/expenses").cookie(admin)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select to_regclass('expense') is not null", Boolean.class)).isTrue();
        JsonNode summary = summary(NOVEMBER);
        assertThat(summary.has("expenses")).isFalse();
        assertThat(summary.has("profit")).isTrue();
    }

    @Test
    void financeRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/finance/summary")).andExpect(status().isUnauthorized());
        // Sem período: mês atual (novembro de 2026).
        mockMvc.perform(get("/api/admin/finance/summary").cookie(admin))
                .andExpect(jsonPath("$.from").value("2026-11-01"))
                .andExpect(jsonPath("$.to").value("2026-11-30"));
    }
}
