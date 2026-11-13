package br.com.confeitaria.modules.payment;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.modules.auth.service.AuthRateLimiter;
import br.com.confeitaria.modules.deadline.DeadlineService;
import br.com.confeitaria.modules.payment.entity.Payment;
import br.com.confeitaria.modules.payment.service.PaymentService;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.Breakdown;
import br.com.confeitaria.modules.tracking.TrackingService;
import br.com.confeitaria.support.AuthTestSupport;
import br.com.confeitaria.support.IntegrationTest;
import br.com.confeitaria.support.MutableClock;
import br.com.confeitaria.support.OrderFixtures;
import br.com.confeitaria.support.OrderFixtures.Catalog;
import br.com.confeitaria.support.OrderFixtures.CreatedOrder;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Requisitos confirmados pela proprietária: prazo do sinal até 24 h antes da data, link de pagamento no cartão,
 * conferência manual do comprovante, saldo de 50% na retirada/entrega, taxa de entrega configurável (R$ 20,00),
 * subtotais do orçamento e massas confirmadas.
 * Pedidos de teste: retirada na terça 03/11/2026 às 14:00 (17:00 UTC); "agora" é 02/11/2026 12:00 UTC.
 */
@IntegrationTest
class DepositAndDeliveryRulesIntegrationTests {

    private static final String NOVEMBER = "?from=2026-11-01&to=2026-11-30";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderFixtures fixtures;

    @Autowired
    private PaymentService payments;

    @Autowired
    private TrackingService tracking;

    @Autowired
    private DeadlineService deadlines;

    @Autowired
    private MutableClock clock;

    @Autowired
    private AuthTestSupport auth;

    @Autowired
    private AuthRateLimiter authLimiter;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    private Catalog catalog;

    @BeforeEach
    void setUp() {
        authLimiter.clear();
        catalog = fixtures.prepare();
    }

    @AfterEach
    void restoreSettings() {
        jdbc.update("update bakery_settings set deposit_cutoff_hours = 0, card_payment_link = null, delivery_fee = 20.00");
    }

    private String commercialStatus(long orderId) {
        return jdbc.queryForObject("select commercial_status from cake_order where id = ?", String.class, orderId);
    }

    private static MockMultipartFile proof() {
        return new MockMultipartFile("proof", "comprovante.pdf", "application/pdf", "%PDF-1.4 comprovante".getBytes());
    }

    @Test
    void depositMustBePaidUntil24HoursBeforeTheScheduledDateAndUnpaidOrdersAreCancelled() {
        jdbc.update("update bakery_settings set deposit_cutoff_hours = 24");
        CreatedOrder order = fixtures.acceptedOrder(catalog);

        // 48 h após o aceite venceria em 04/11; o limite de 24 h antes da retirada (03/11 17:00 UTC) vence antes.
        assertThat(payments.summary(order.id()).depositDueAt()).isEqualTo(Instant.parse("2026-11-02T17:00:00Z"));
        assertThat(tracking.view(order.id()).payment().depositDueAt()).isEqualTo(Instant.parse("2026-11-02T17:00:00Z"));

        clock.advance(Duration.ofHours(4));
        deadlines.runAll();
        assertThat(commercialStatus(order.id())).isEqualTo("AWAITING_DEPOSIT");
        assertThat(jdbc.queryForObject("select count(*) from notification where type = 'DEPOSIT_DEADLINE_NEAR' "
                + "and order_id = ?", Integer.class, order.id())).isEqualTo(1);

        clock.advance(Duration.ofHours(1));
        deadlines.runAll();
        assertThat(commercialStatus(order.id())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from slot_reservation where order_id = ? and status = 'ACTIVE'",
                Integer.class, order.id())).isZero();
    }

    @Test
    void cardLinkProofWaitsForManualConfirmationAndTheBalanceIsRegisteredAtPickup() throws Exception {
        jdbc.update("update bakery_settings set card_payment_link = 'https://pagamento.exemplo.test/link'");
        var owner = auth.createUser();
        Cookie admin = login(mockMvc, owner.getEmail(), PASSWORD);
        JsonNode before = summary(admin);
        CreatedOrder order = fixtures.acceptedOrder(catalog);

        var view = tracking.view(order.id());
        assertThat(view.payment().cardPaymentLink()).isEqualTo("https://pagamento.exemplo.test/link");
        Breakdown breakdown = view.quote().breakdown();
        assertThat(breakdown.products()).isEqualByComparingTo("120.00");
        assertThat(breakdown.extras()).isEqualByComparingTo("25.00");
        assertThat(breakdown.topper()).isEqualByComparingTo("20.00");
        assertThat(breakdown.delivery()).isEqualByComparingTo("0.00");
        assertThat(view.quote().depositAmount()).isEqualByComparingTo("82.50");
        assertThat(view.quote().balanceAmount()).isEqualByComparingTo("82.50");

        assertThatThrownBy(() -> payments.submitProof(order.id(), proof(), null, Payment.Method.CASH))
                .isInstanceOf(FieldValidationException.class);
        var submitted = payments.submitProof(order.id(), proof(), null, Payment.Method.CARD_LINK);
        assertThat(submitted.payments()).singleElement().satisfies(p -> {
            assertThat(p.method()).isEqualTo(Payment.Method.CARD_LINK);
            assertThat(p.status()).isEqualTo(Payment.Status.PENDING_REVIEW);
            assertThat(p.amount()).isEqualByComparingTo("82.50");
        });
        // Comprovante enviado não confirma nada sozinho.
        assertThat(commercialStatus(order.id())).isEqualTo("AWAITING_DEPOSIT");
        assertThat(BigDecimal.ZERO).isEqualByComparingTo(delta(before, summary(admin), "received"));

        payments.confirm(submitted.payments().get(0).id(), null, false, owner.getId());
        assertThat(commercialStatus(order.id())).isEqualTo("CONFIRMED");
        JsonNode confirmed = summary(admin);
        assertThat(delta(before, confirmed, "received")).isEqualByComparingTo("82.50");
        assertThat(delta(before, confirmed, "pending")).isEqualByComparingTo("82.50");
        assertThat(delta(before, confirmed, "forecastRevenue")).isEqualByComparingTo("165.00");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/admin/orders/" + order.id() + "/payments").with(csrf()).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"BALANCE\",\"method\":\"PIX\",\"amount\":\"82.50\"}"))
                .andExpect(status().isOk());
        JsonNode paid = summary(admin);
        assertThat(delta(before, paid, "received")).isEqualByComparingTo("165.00");
        assertThat(delta(before, paid, "profit")).isEqualByComparingTo("165.00");
        assertThat(delta(before, paid, "pending")).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("select payment_status from cake_order where id = ?", String.class, order.id()))
                .isEqualTo("PAID");
    }

    @Test
    void deliveryFeeAndCardLinkAreConfigurableAndTheFeeIsPublicOnlyWithDelivery() throws Exception {
        Cookie admin = login(mockMvc, auth.createUser().getEmail(), PASSWORD);
        jdbc.update("update bakery_settings set delivery_enabled = true");
        ObjectNode settings = (ObjectNode) json.readTree(mockMvc.perform(get("/api/admin/settings").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryFee").value("20.00"))
                .andReturn().getResponse().getContentAsString());

        settings.put("cardPaymentLink", "http://inseguro.test");
        mockMvc.perform(put("/api/admin/settings").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(settings.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cardPaymentLink"));

        settings.put("cardPaymentLink", "https://pagamento.exemplo.test/abc");
        settings.put("deliveryFee", "25.50");
        settings.put("depositCutoffHours", 24);
        mockMvc.perform(put("/api/admin/settings").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(settings.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryFee").value("25.50"))
                .andExpect(jsonPath("$.cardPaymentLink").value("https://pagamento.exemplo.test/abc"))
                .andExpect(jsonPath("$.depositCutoffHours").value(24));

        mockMvc.perform(get("/api/public/bakery"))
                .andExpect(jsonPath("$.deliveryFee").value("25.50"))
                .andExpect(jsonPath("$.cardPaymentLink").doesNotExist());
        jdbc.update("update bakery_settings set delivery_enabled = false");
        mockMvc.perform(get("/api/public/bakery")).andExpect(jsonPath("$.deliveryFee").doesNotExist());
    }

    @Test
    void confirmedFlavorsExistInTheCatalog() {
        assertThat(jdbc.queryForObject("select count(*) from cake_flavor where lower(name) in "
                + "('baunilha', 'chocolate', 'red velvet')", Integer.class)).isEqualTo(3);
    }

    private JsonNode summary(Cookie admin) throws Exception {
        return json.readTree(mockMvc.perform(get("/api/admin/finance/summary" + NOVEMBER).cookie(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static BigDecimal delta(JsonNode before, JsonNode after, String field) {
        return new BigDecimal(after.get(field).asText()).subtract(new BigDecimal(before.get(field).asText()));
    }
}
