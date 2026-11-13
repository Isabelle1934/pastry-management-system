package br.com.confeitaria.modules.tracking;

import static br.com.confeitaria.support.AuthTestSupport.uniqueIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.confeitaria.modules.quote.entity.QuoteItemKind;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.DraftRequest;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.ItemRequest;
import br.com.confeitaria.modules.quote.service.QuoteService;
import br.com.confeitaria.support.IntegrationTest;
import br.com.confeitaria.support.MutableClock;
import br.com.confeitaria.support.OrderFixtures;
import br.com.confeitaria.support.OrderFixtures.Catalog;
import br.com.confeitaria.support.OrderFixtures.CreatedOrder;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class TrackingIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderFixtures fixtures;

    @Autowired
    private TrackingAccess access;

    @Autowired
    private QuoteService quotes;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JsonMapper json;

    private Catalog catalog;

    @BeforeEach
    void setUp() {
        catalog = fixtures.prepare();
        access.clearLimits();
    }

    private ResultActions verify(String code, String last4, RequestPostProcessor ip) throws Exception {
        return mockMvc.perform(post("/api/tracking/verify").with(csrf()).with(ip).contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackingCode\":\"" + code + "\",\"whatsappLast4\":\"" + last4 + "\"}"));
    }

    private Cookie verifiedSession(CreatedOrder order) throws Exception {
        return verify(order.trackingCode(), "4321", uniqueIp()).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, Cookie session, String body) throws Exception {
        return mockMvc.perform(builder.with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "" : body));
    }

    private long sendQuote(long orderId) {
        quotes.saveDraft(orderId, new DraftRequest(List.of(
                new ItemRequest(QuoteItemKind.PRODUCT, "Bolo personalizado", null, new java.math.BigDecimal("1"), new java.math.BigDecimal("120.00")),
                new ItemRequest(QuoteItemKind.TOPPER, "Topo", null, new java.math.BigDecimal("1"), new java.math.BigDecimal("20.00"))), null, 48));
        return quotes.send(orderId, 1L).id();
    }

    private String commercialStatus(long orderId) {
        return jdbc.queryForObject("select commercial_status from cake_order where id = ?", String.class, orderId);
    }

    @Test
    void customerSeesTheOrderWithCodeAndLastDigits() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        String typed = order.trackingCode().toLowerCase().replace("-", " ");

        verify(typed, "4321", uniqueIp())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value(order.trackingCode()))
                .andExpect(jsonPath("$.whatsappMasked").value("(11) *****-4321"))
                .andExpect(jsonPath("$.commercialStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.quote").doesNotExist())
                .andExpect(jsonPath("$.history").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void wrongDigitsAndUnknownCodesGetTheSameAnswerAndAreLimited() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        String wrongDigits = verify(order.trackingCode(), "0000", uniqueIp()).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownCode = verify("ZZZZ-ZZZZ-ZZZZ-ZZZZ", "4321", uniqueIp()).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(wrongDigits).get("detail")).isEqualTo(json.readTree(unknownCode).get("detail"));

        for (int i = 0; i < 4; i++) {
            verify(order.trackingCode(), "1111", uniqueIp()).andExpect(status().isUnauthorized());
        }
        verify(order.trackingCode(), "4321", uniqueIp())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void accessRequiresVerificationAndExpires() throws Exception {
        mockMvc.perform(get("/api/tracking/order"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TRACKING_REQUIRED"));

        Cookie session = verifiedSession(fixtures.create(catalog));
        mockMvc.perform(get("/api/tracking/order").cookie(session)).andExpect(status().isOk());

        clock.advance(Duration.ofHours(2).plusMinutes(1));
        mockMvc.perform(get("/api/tracking/order").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsAccessAndASessionSeesOnlyItsOwnOrder() throws Exception {
        CreatedOrder first = fixtures.create(catalog);
        CreatedOrder second = fixtures.create(catalog);
        Cookie session = verifiedSession(first);

        mockMvc.perform(get("/api/tracking/order").cookie(session))
                .andExpect(jsonPath("$.trackingCode").value(first.trackingCode()));
        long secondQuote = sendQuote(second.id());
        send(post("/api/tracking/order/quotes/" + secondQuote + "/accept"), session, null)
                .andExpect(status().isNotFound());
        assertThat(commercialStatus(second.id())).isEqualTo("QUOTE_SENT");

        send(post("/api/tracking/logout"), session, null).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tracking/order").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void customerAcceptsTheCurrentQuote() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        long quoteId = sendQuote(order.id());
        Cookie session = verifiedSession(order);

        mockMvc.perform(get("/api/tracking/order").cookie(session))
                .andExpect(jsonPath("$.quote.total").value("140.00"))
                .andExpect(jsonPath("$.quote.depositAmount").value("70.00"))
                .andExpect(jsonPath("$.quote.items[0].name").value("Bolo personalizado"));

        send(post("/api/tracking/order/quotes/" + quoteId + "/accept"), session, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commercialStatus").value("AWAITING_DEPOSIT"))
                .andExpect(jsonPath("$.quote.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.editable").value(false));
        send(post("/api/tracking/order/quotes/" + quoteId + "/accept"), session, null)
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from slot_occupancy where reserved_count > 0", Integer.class))
                .as("aceite não ocupa vaga; só o sinal confirmado").isZero();
    }

    @Test
    void expiredQuoteCannotBeAccepted() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        long quoteId = sendQuote(order.id());
        Cookie session = verifiedSession(order);
        clock.advance(Duration.ofHours(49));
        session = verifiedSession(order);

        send(post("/api/tracking/order/quotes/" + quoteId + "/accept"), session, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_EXPIRED"));
    }

    @Test
    void customerDeclinesWithAReason() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        long quoteId = sendQuote(order.id());
        Cookie session = verifiedSession(order);

        send(post("/api/tracking/order/quotes/" + quoteId + "/decline"), session, "{\"reason\":\"Achei caro\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commercialStatus").value("DECLINED_BY_CLIENT"));
        assertThat(jdbc.queryForObject("select decline_reason from quote where id = ?", String.class, quoteId))
                .isEqualTo("Achei caro");
    }

    @Test
    void editingAfterTheQuoteWasSentInvalidatesItAndReturnsToReview() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        long quoteId = sendQuote(order.id());
        Cookie session = verifiedSession(order);
        long version = json.readTree(mockMvc.perform(get("/api/tracking/order").cookie(session))
                .andReturn().getResponse().getContentAsString()).get("version").asLong();
        Map<String, Object> edit = new java.util.LinkedHashMap<>(Map.of(
                "pickupDate", "2026-11-06", "pickupTime", "14:00", "sizeId", catalog.sizeId(),
                "flavorIds", List.of(catalog.flavorId(), catalog.otherFlavorId()), "fillingIds", List.of(catalog.fillingId()),
                "topperRequested", false, "version", version));

        send(put("/api/tracking/order"), session, json.writeValueAsString(edit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commercialStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.pickupDate").value("2026-11-06"))
                .andExpect(jsonPath("$.quote").doesNotExist());
        assertThat(jdbc.queryForObject("select status from quote where id = ?", String.class, quoteId)).isEqualTo("SUPERSEDED");
        assertThat(jdbc.queryForObject("select count(*) from order_history where order_id = ? and event_type = 'EDITED_BY_CLIENT'",
                Integer.class, order.id())).isEqualTo(1);

        // Depois do aceite, a cliente não edita mais.
        long newQuote = sendQuote(order.id());
        send(post("/api/tracking/order/quotes/" + newQuote + "/accept"), session, null).andExpect(status().isOk());
        edit.put("version", json.readTree(mockMvc.perform(get("/api/tracking/order").cookie(session))
                .andReturn().getResponse().getContentAsString()).get("version").asLong());
        send(put("/api/tracking/order"), session, json.writeValueAsString(edit))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_EDITABLE"));
    }

    @Test
    void customerCannotPickAnUnavailableTimeWhenEditing() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        Cookie session = verifiedSession(order);
        long version = json.readTree(mockMvc.perform(get("/api/tracking/order").cookie(session))
                .andReturn().getResponse().getContentAsString()).get("version").asLong();
        String body = json.writeValueAsString(Map.of("pickupDate", "2026-11-04", "pickupTime", "14:00",
                "sizeId", catalog.sizeId(), "flavorIds", List.of(catalog.flavorId()), "fillingIds", List.of(catalog.fillingId()),
                "topperRequested", false, "version", version));

        send(put("/api/tracking/order"), session, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("pickupTime"));
    }
}
