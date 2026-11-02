package br.com.confeitaria.modules.finance;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
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
 * verificados pela diferença antes/depois; despesas usam períodos que nenhum outro teste usa.
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

    private long categoryId(String name) {
        return jdbc.queryForObject("select id from expense_category where name = ?", Long.class, name);
    }

    private JsonNode createExpense(String description, String amount, String date, long categoryId, String orderCode)
            throws Exception {
        return read(send(post("/api/admin/finance/expenses"), "{\"description\":\"" + description + "\",\"amount\":\""
                + amount + "\",\"expenseDate\":\"" + date + "\",\"categoryId\":" + categoryId
                + (orderCode == null ? "" : ",\"orderCode\":\"" + orderCode + "\"") + "}")
                .andExpect(status().isCreated()));
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
        assertThat(delta(start, paid, "cashResult")).isEqualByComparingTo("155.00");
        assertThat(delta(start, paid, "estimatedProfit")).isEqualByComparingTo("165.00");

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
    void expenseLinkedToAnOrderIsCountedOnceAndDeletionIsOnlyLogical() throws Exception {
        String period = "?from=2031-03-01&to=2031-03-31";
        CreatedOrder order = fixtures.create(catalog);
        long ingredients = categoryId("Ingredientes");
        long packaging = categoryId("Embalagens");

        JsonNode general = createExpense("Farinha e açúcar", "50.00", "2031-03-10", ingredients, null);
        assertThat(general.get("order").isNull()).isTrue();
        JsonNode linked = createExpense("Caixa especial", "30.00", "2031-03-12", packaging,
                order.trackingCode().toLowerCase().replace("-", " "));
        assertThat(linked.get("order").get("id").asLong()).isEqualTo(order.id());
        assertThat(linked.get("order").get("trackingCode").asText()).isEqualTo(order.trackingCode());
        createExpense("Fora do período", "999.00", "2031-04-01", ingredients, null);

        JsonNode summary = summary(period);
        assertThat(money(summary, "expenses")).isEqualByComparingTo("80.00");
        assertThat(money(summary, "orderExpenses")).isEqualByComparingTo("30.00");
        assertThat(money(summary, "estimatedProfit")).isEqualByComparingTo("-80.00");
        assertThat(money(summary, "cashResult")).isEqualByComparingTo("-80.00");
        assertThat(summary.get("expensesByCategory")).hasSize(2);
        assertThat(summary.get("expensesByCategory").get(0).get("name").asText()).isEqualTo("Ingredientes");
        assertThat(summary.get("expensesByCategory").get(0).get("total").asText()).isEqualTo("50.00");

        mockMvc.perform(get("/api/admin/finance/expenses" + period + "&orderId=" + order.id()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Caixa especial"))
                .andExpect(jsonPath("$.content[0].order.customerName").value("Ana Souza"));
        mockMvc.perform(get("/api/admin/finance/expenses" + period + "&categoryId=" + ingredients).cookie(admin))
                .andExpect(jsonPath("$.totalElements").value(1));

        long id = general.get("id").asLong();
        mockMvc.perform(delete("/api/admin/finance/expenses/" + id + "?version=" + general.get("version").asLong())
                .with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        assertThat(money(summary(period), "expenses")).isEqualByComparingTo("30.00");
        mockMvc.perform(get("/api/admin/finance/expenses" + period).cookie(admin))
                .andExpect(jsonPath("$.totalElements").value(1));
        assertThat(jdbc.queryForObject("select deleted_at is not null from expense where id = ?", Boolean.class, id))
                .isTrue();
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'EXPENSE_DELETED' "
                + "and entity_id = ?", Integer.class, String.valueOf(id))).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("delete from expense where id = ?", id))
                .isInstanceOf(DataAccessException.class);
        send(put("/api/admin/finance/expenses/" + id), "{\"description\":\"x\",\"amount\":\"1.00\","
                + "\"expenseDate\":\"2031-03-10\",\"categoryId\":" + ingredients + ",\"version\":1}")
                .andExpect(status().isNotFound());
    }

    @Test
    void expenseEditUsesVersionAndValidatesInput() throws Exception {
        long ingredients = categoryId("Ingredientes");
        JsonNode expense = createExpense("Chocolate", "40.00", "2031-05-02", ingredients, null);
        long id = expense.get("id").asLong();
        long version = expense.get("version").asLong();

        send(put("/api/admin/finance/expenses/" + id), "{\"description\":\"Chocolate belga\",\"amount\":\"45.90\","
                + "\"expenseDate\":\"2031-05-03\",\"categoryId\":" + ingredients + ",\"version\":" + version + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("45.90"))
                .andExpect(jsonPath("$.description").value("Chocolate belga"));
        send(put("/api/admin/finance/expenses/" + id), "{\"description\":\"Outra\",\"amount\":\"1.00\","
                + "\"expenseDate\":\"2031-05-03\",\"categoryId\":" + ingredients + ",\"version\":" + version + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        send(post("/api/admin/finance/expenses"), "{\"description\":\"\",\"amount\":\"0\",\"categoryId\":"
                + ingredients + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
        send(post("/api/admin/finance/expenses"), "{\"description\":\"Gás\",\"amount\":\"10.001\","
                + "\"expenseDate\":\"2031-05-02\",\"categoryId\":" + ingredients + "}")
                .andExpect(status().isBadRequest());
        send(post("/api/admin/finance/expenses"), "{\"description\":\"Gás\",\"amount\":\"10.00\","
                + "\"expenseDate\":\"2031-05-02\",\"categoryId\":" + ingredients + ",\"orderCode\":\"ZZZZ-ZZZZ-ZZZZ-ZZZZ\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("orderCode"));
        send(post("/api/admin/finance/expenses"), "{\"description\":\"Gás\",\"amount\":\"10.00\","
                + "\"expenseDate\":\"2031-05-02\",\"categoryId\":999999}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));

        mockMvc.perform(get("/api/admin/finance/summary?from=2026-01-01&to=2027-01-02").cookie(admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/finance/summary?from=2026-02-01&to=2026-01-01").cookie(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categoriesAreUniqueAndDeactivatedOnesOnlyStayOnOldExpenses() throws Exception {
        String name = "Gás " + UUID.randomUUID().toString().substring(0, 6);
        JsonNode category = read(send(post("/api/admin/finance/categories"), "{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated()));
        long id = category.get("id").asLong();
        send(post("/api/admin/finance/categories"), "{\"name\":\"" + name.toUpperCase() + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));

        JsonNode expense = createExpense("Botijão", "120.00", "2031-06-01", id, null);
        JsonNode deactivated = read(send(patch("/api/admin/finance/categories/" + id + "/active"),
                "{\"active\":false,\"version\":" + category.get("version").asLong() + "}").andExpect(status().isOk()));
        assertThat(deactivated.get("active").asBoolean()).isFalse();

        send(post("/api/admin/finance/expenses"), "{\"description\":\"Outro botijão\",\"amount\":\"120.00\","
                + "\"expenseDate\":\"2031-06-02\",\"categoryId\":" + id + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
        send(put("/api/admin/finance/expenses/" + expense.get("id").asLong()), "{\"description\":\"Botijão 13 kg\","
                + "\"amount\":\"120.00\",\"expenseDate\":\"2031-06-01\",\"categoryId\":" + id + ",\"version\":"
                + expense.get("version").asLong() + "}")
                .andExpect(status().isOk());

        send(put("/api/admin/finance/categories/" + id), "{\"name\":\"" + name + " (botijão)\",\"version\":"
                + deactivated.get("version").asLong() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name + " (botijão)"));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'expense_category' "
                + "and entity_id = ?", Integer.class, String.valueOf(id))).isEqualTo(3);
    }

    @Test
    void financeRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/finance/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/finance/expenses")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/finance/categories").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/finance/categories").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\"}")).andExpect(status().isForbidden());
        // Sem período: mês atual (novembro de 2026).
        mockMvc.perform(get("/api/admin/finance/summary").cookie(admin))
                .andExpect(jsonPath("$.from").value("2026-11-01"))
                .andExpect(jsonPath("$.to").value("2026-11-30"));
    }
}
