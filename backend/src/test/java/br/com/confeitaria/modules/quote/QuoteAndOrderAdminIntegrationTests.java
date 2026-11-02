package br.com.confeitaria.modules.quote;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.confeitaria.modules.auth.service.AuthRateLimiter;
import br.com.confeitaria.modules.schedule.service.ReservationService;
import br.com.confeitaria.support.AuthTestSupport;
import br.com.confeitaria.support.IntegrationTest;
import br.com.confeitaria.support.OrderFixtures;
import br.com.confeitaria.support.OrderFixtures.Catalog;
import br.com.confeitaria.support.OrderFixtures.CreatedOrder;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class QuoteAndOrderAdminIntegrationTests {

    private static final String THREE_ITEMS = """
            {"items":[
              {"name":"Bolo personalizado","quantity":"1","unitPrice":"120.00"},
              {"name":"Decoração especial","description":"Flores de açúcar","quantity":"1","unitPrice":"25.00"},
              {"name":"Topo personalizado","quantity":"1","unitPrice":"20.00"}],
             "notes":"Inclui embalagem"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTestSupport auth;

    @Autowired
    private AuthRateLimiter rateLimiter;

    @Autowired
    private OrderFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private ReservationService reservations;

    @Autowired
    private JsonMapper json;

    private Cookie session;
    private Catalog catalog;

    @BeforeEach
    void setUp() throws Exception {
        rateLimiter.clear();
        catalog = fixtures.prepare();
        session = login(mockMvc, auth.createUser().getEmail(), PASSWORD);
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, String body) throws Exception {
        return mockMvc.perform(builder.with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "" : body));
    }

    private JsonNode detail(long orderId) throws Exception {
        return json.readTree(mockMvc.perform(get("/api/admin/orders/" + orderId).cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void sendQuote(long orderId, String draft) throws Exception {
        send(put("/api/admin/orders/" + orderId + "/quotes/draft"), draft).andExpect(status().isOk());
        send(post("/api/admin/orders/" + orderId + "/quotes/send"), null).andExpect(status().isOk());
    }

    private String commercialStatus(long orderId) {
        return jdbc.queryForObject("select commercial_status from cake_order where id = ?", String.class, orderId);
    }

    // --- Orçamentos ---

    @Test
    void quoteTotalsAreComputedOnTheServerAndSendingUpdatesTheOrder() throws Exception {
        CreatedOrder order = fixtures.create(catalog);

        send(put("/api/admin/orders/" + order.id() + "/quotes/draft"), THREE_ITEMS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[1].subtotal").value("25.00"))
                .andExpect(jsonPath("$.total").value("165.00"))
                .andExpect(jsonPath("$.depositAmount").value("82.50"))
                .andExpect(jsonPath("$.balanceAmount").value("82.50"))
                .andExpect(jsonPath("$.validityHours").value(org.hamcrest.Matchers.greaterThan(0)));
        assertThat(commercialStatus(order.id())).as("rascunho não muda o pedido").isEqualTo("PENDING_REVIEW");

        send(post("/api/admin/orders/" + order.id() + "/quotes/send"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.validUntil").exists());
        assertThat(commercialStatus(order.id())).isEqualTo("QUOTE_SENT");
        assertThat(detail(order.id()).get("history").get(1).get("eventType").asText()).isEqualTo("QUOTE_SENT");
    }

    @Test
    void depositIsHalfRoundedToCentsAndBalanceClosesTheTotal() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        send(put("/api/admin/orders/" + order.id() + "/quotes/draft"),
                "{\"items\":[{\"name\":\"Bolo\",\"quantity\":\"3\",\"unitPrice\":\"55.185\"}]}")
                .andExpect(status().isBadRequest());
        send(put("/api/admin/orders/" + order.id() + "/quotes/draft"),
                "{\"items\":[{\"name\":\"Bolo\",\"quantity\":\"3\",\"unitPrice\":\"55.19\"},"
                        + "{\"name\":\"Fita\",\"quantity\":\"0.5\",\"unitPrice\":\"0.03\"}]}")
                .andExpect(jsonPath("$.items[1].subtotal").value("0.02"))
                .andExpect(jsonPath("$.total").value("165.59"))
                .andExpect(jsonPath("$.depositAmount").value("82.80"))
                .andExpect(jsonPath("$.balanceAmount").value("82.79"));
    }

    @Test
    void newVersionsKeepTheHistoryOfPreviousOnes() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        sendQuote(order.id(), THREE_ITEMS);
        sendQuote(order.id(), "{\"items\":[{\"name\":\"Bolo com desconto\",\"quantity\":\"1\",\"unitPrice\":\"150.00\"}]}");

        mockMvc.perform(get("/api/admin/orders/" + order.id() + "/quotes").cookie(session))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[1].versionNumber").value(1))
                .andExpect(jsonPath("$[1].status").value("SUPERSEDED"))
                .andExpect(jsonPath("$[1].total").value("165.00"));
    }

    @Test
    void sentQuotesCannotBeChangedEvenDirectlyInTheDatabase() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        sendQuote(order.id(), THREE_ITEMS);
        long quoteId = jdbc.queryForObject("select id from quote where order_id = ?", Long.class, order.id());

        assertThatThrownBy(() -> jdbc.update("update quote_item set unit_price = 1, subtotal = 1 where quote_id = ?", quoteId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update quote set total = 1, deposit_amount = 0.5 where id = ?", quoteId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from quote where id = ?", quoteId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from quote_item where quote_id = ?", quoteId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void draftRulesAreEnforced() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        send(post("/api/admin/orders/" + order.id() + "/quotes/send"), null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUOTE_EMPTY"));
        send(put("/api/admin/orders/" + order.id() + "/quotes/draft"), "{\"items\":[]}")
                .andExpect(status().isBadRequest());
        send(put("/api/admin/orders/" + order.id() + "/quotes/draft"),
                "{\"items\":[{\"name\":\"Brinde\",\"quantity\":\"1\",\"unitPrice\":\"0\"}]}")
                .andExpect(status().isOk());
        send(post("/api/admin/orders/" + order.id() + "/quotes/send"), null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUOTE_EMPTY"));
        mockMvc.perform(delete("/api/admin/orders/" + order.id() + "/quotes/draft").with(csrf()).cookie(session))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from quote where order_id = ?", Integer.class, order.id())).isZero();
    }

    // --- Recusa e cancelamento ---

    @Test
    void rejectingIsOnlyPossibleBeforeAcceptanceAndInvalidatesThePendingQuote() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        sendQuote(order.id(), THREE_ITEMS);
        long version = detail(order.id()).get("version").asLong();

        send(post("/api/admin/orders/" + order.id() + "/reject"), "{\"reason\":\"Sem disponibilidade\",\"version\":" + version + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commercialStatus").value("REJECTED"))
                .andExpect(jsonPath("$.quotes[0].status").value("SUPERSEDED"));
        long newVersion = detail(order.id()).get("version").asLong();
        send(post("/api/admin/orders/" + order.id() + "/cancel"), "{\"reason\":\"x\",\"version\":" + newVersion + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void cancellingReleasesTheReservedSlotAndRecordsTheReason() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        tx.executeWithoutResult(s -> reservations.reserve(order.id(), OrderFixtures.TUESDAY, OrderFixtures.TWO_PM));
        long version = detail(order.id()).get("version").asLong();

        send(post("/api/admin/orders/" + order.id() + "/cancel"), "{\"reason\":\"Cliente desistiu\",\"version\":" + version + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commercialStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Cliente desistiu"))
                .andExpect(jsonPath("$.cancelledBy").value("OWNER"))
                .andExpect(jsonPath("$.slotReserved").value(false));
        assertThat(jdbc.queryForObject("select reserved_count from slot_occupancy where slot_date = ?", Integer.class,
                OrderFixtures.TUESDAY)).isZero();
        send(post("/api/admin/orders/" + order.id() + "/cancel"), "{\"reason\":\"\",\"version\":" + version + "}")
                .andExpect(status().isBadRequest());
    }

    // --- Edição pela proprietária ---

    @Test
    void ownerEditsTheOrderWithHistoryAndConcurrencyControl() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        long version = detail(order.id()).get("version").asLong();
        Map<String, Object> body = new java.util.LinkedHashMap<>(Map.of(
                "customerName", "Ana Souza Lima", "customerWhatsapp", "11987654321",
                "pickupDate", "2026-11-06", "pickupTime", "14:00", "sizeId", catalog.sizeId(),
                "flavorIds", List.of(catalog.flavorId(), catalog.otherFlavorId()), "fillingIds", List.of(catalog.fillingId()),
                "decoration", "Flores e borboletas", "topperRequested", true, "topperTheme", "Borboletas"));
        body.put("version", version);

        send(put("/api/admin/orders/" + order.id()), json.writeValueAsString(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Ana Souza Lima"))
                .andExpect(jsonPath("$.pickupDate").value("2026-11-06"))
                .andExpect(jsonPath("$.flavors", hasSize(2)))
                .andExpect(jsonPath("$.topperTheme").value("Borboletas"))
                .andExpect(jsonPath("$.history[*].eventType", hasItem("EDITED_BY_OWNER")));

        send(put("/api/admin/orders/" + order.id()), json.writeValueAsString(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        body.put("version", detail(order.id()).get("version").asLong());
        body.put("pickupTime", "09:30");
        send(put("/api/admin/orders/" + order.id()), json.writeValueAsString(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("pickupTime"));
    }

    @Test
    void reschedulingAConfirmedOrderMovesItsSlot() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        tx.executeWithoutResult(s -> reservations.reserve(order.id(), OrderFixtures.TUESDAY, OrderFixtures.TWO_PM));
        Map<String, Object> body = new java.util.LinkedHashMap<>(Map.of(
                "customerName", "Ana Souza", "customerWhatsapp", "11987654321",
                "pickupDate", "2026-11-06", "pickupTime", "14:00", "sizeId", catalog.sizeId(),
                "flavorIds", List.of(catalog.flavorId()), "fillingIds", List.of(catalog.fillingId()),
                "topperRequested", false));
        body.put("version", detail(order.id()).get("version").asLong());

        send(put("/api/admin/orders/" + order.id()), json.writeValueAsString(body)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select slot_date from slot_reservation where order_id = ? and status = 'ACTIVE'",
                java.time.LocalDate.class, order.id())).isEqualTo(OrderFixtures.FRIDAY);
    }

    // --- Consulta ---

    @Test
    void listFiltersSearchAndCounts() throws Exception {
        CreatedOrder first = fixtures.create(catalog);
        CreatedOrder second = fixtures.create(catalog);
        sendQuote(second.id(), THREE_ITEMS);

        mockMvc.perform(get("/api/admin/orders").cookie(session).param("q", second.trackingCode().substring(0, 9)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].trackingCode").value(second.trackingCode()))
                .andExpect(jsonPath("$.content[0].quoteTotal").value("165.00"));
        mockMvc.perform(get("/api/admin/orders").cookie(session).param("status", "PENDING_REVIEW").param("size", "100"))
                .andExpect(jsonPath("$.content[*].id", hasItem((int) first.id())));
        mockMvc.perform(get("/api/admin/orders").cookie(session).param("q", "98765-4321"))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
        mockMvc.perform(get("/api/admin/orders/counts").cookie(session))
                .andExpect(jsonPath("$.QUOTE_SENT").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/admin/orders/999999").cookie(session)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void ownerDownloadsOnlyFilesOfTheRequestedOrder() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        CreatedOrder other = fixtures.create(catalog);
        byte[] jpeg = new byte[64];
        jpeg[0] = (byte) 0xFF;
        jpeg[1] = (byte) 0xD8;
        jpeg[2] = (byte) 0xFF;
        Long fileId = tx.execute(s -> {
            var valid = fileStorage.validate(new org.springframework.mock.web.MockMultipartFile("f", "bolo.jpg", "image/png", jpeg),
                    br.com.confeitaria.modules.file.entity.FilePurpose.ORDER_REFERENCE, "f");
            return fileStorage.store(valid, order.id(), br.com.confeitaria.modules.file.entity.FilePurpose.ORDER_REFERENCE,
                    br.com.confeitaria.common.audit.ActorType.CLIENT).getId();
        });

        mockMvc.perform(get("/api/admin/orders/" + order.id() + "/files/" + fileId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Content-Disposition", startsWith("inline")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mockMvc.perform(get("/api/admin/orders/" + other.id() + "/files/" + fileId).cookie(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/orders/" + order.id() + "/files/" + fileId))
                .andExpect(status().isUnauthorized());
    }

    @Autowired
    private br.com.confeitaria.modules.file.service.FileStorageService fileStorage;
}
