package br.com.confeitaria.modules.payment;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.login;
import static br.com.confeitaria.support.AuthTestSupport.uniqueIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.confeitaria.modules.auth.service.AuthRateLimiter;
import br.com.confeitaria.modules.schedule.service.ReservationService;
import br.com.confeitaria.modules.tracking.TrackingAccess;
import br.com.confeitaria.support.AuthTestSupport;
import br.com.confeitaria.support.IntegrationTest;
import br.com.confeitaria.support.OrderFixtures;
import br.com.confeitaria.support.OrderFixtures.Catalog;
import br.com.confeitaria.support.OrderFixtures.CreatedOrder;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Pedidos de teste: orçamento de R$ 165,00, sinal de R$ 82,50, retirada terça 03/11 às 14:00 (limite 3). */
@IntegrationTest
class PaymentFlowIntegrationTests {

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
    private ReservationService reservations;

    @Autowired
    private TransactionTemplate tx;

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

    private static byte[] pdf() {
        return "%PDF-1.7\ncomprovante".getBytes(StandardCharsets.US_ASCII);
    }

    private Cookie client(CreatedOrder order) throws Exception {
        return mockMvc.perform(post("/api/tracking/verify").with(csrf()).with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingCode\":\"" + order.trackingCode() + "\",\"whatsappLast4\":\"4321\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
    }

    private ResultActions sendProof(Cookie session, String name, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/tracking/order/payments/deposit-proof")
                .file(new MockMultipartFile("proof", name, "application/pdf", content))
                .with(csrf()).cookie(session));
    }

    private ResultActions adminPost(String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : body));
    }

    private long pendingPaymentId(long orderId) {
        return jdbc.queryForObject("select id from payment where order_id = ? and status = 'PENDING_REVIEW'",
                Long.class, orderId);
    }

    private String commercialStatus(long orderId) {
        return jdbc.queryForObject("select commercial_status from cake_order where id = ?", String.class, orderId);
    }

    @Test
    void clientSendsProofAndOwnerConfirmationConfirmsTheOrderAndReservesTheSlot() throws Exception {
        CreatedOrder order = fixtures.acceptedOrder(catalog);
        Cookie session = client(order);

        mockMvc.perform(get("/api/tracking/order").cookie(session))
                .andExpect(jsonPath("$.payment.depositAmount").value("82.50"))
                .andExpect(jsonPath("$.payment.canSubmitProof").value(true));

        sendProof(session, "comprovante.pdf", pdf())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("DEPOSIT_UNDER_REVIEW"))
                .andExpect(jsonPath("$.payment.canSubmitProof").value(false))
                .andExpect(jsonPath("$.payment.payments[0].status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.payment.payments[0].amount").value("82.50"));
        assertThat(jdbc.queryForObject("select count(*) from notification where type = 'PROOF_SUBMITTED' and order_id = ?",
                Integer.class, order.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select message from notification where type = 'PROOF_SUBMITTED' "
                + "and order_id = ?", String.class, order.id())).contains("(R$ 82,50)");
        assertThat(jdbc.queryForObject("select message from notification where type = 'NEW_ORDER' "
                + "and order_id = ?", String.class, order.id())).contains("para 03/11/2026 às 14:00");

        long paymentId = pendingPaymentId(order.id());
        long proofFile = jdbc.queryForObject("select proof_file_id from payment where id = ?", Long.class, paymentId);
        mockMvc.perform(get("/api/admin/orders/" + order.id() + "/files/" + proofFile).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));

        adminPost("/api/admin/payments/" + paymentId + "/confirm", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPOSIT_PAID"))
                .andExpect(jsonPath("$.received").value("82.50"))
                .andExpect(jsonPath("$.balanceDue").value("82.50"));

        assertThat(commercialStatus(order.id())).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select count(*) from slot_reservation where order_id = ? and status = 'ACTIVE'",
                Integer.class, order.id())).isEqualTo(1);
        mockMvc.perform(get("/api/tracking/order").cookie(session))
                .andExpect(jsonPath("$.commercialStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.pixKey").value(org.hamcrest.Matchers.anything()));
    }

    @Test
    void proofRulesAreEnforced() throws Exception {
        CreatedOrder pending = fixtures.create(catalog);
        sendProof(client(pending), "comprovante.pdf", pdf())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROOF_NOT_EXPECTED"));

        CreatedOrder order = fixtures.acceptedOrder(catalog);
        Cookie session = client(order);
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0, 0};
        sendProof(session, "comprovante.webp", webp)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Envie o comprovante em JPG, PNG ou PDF."));
        sendProof(session, "comprovante.pdf", "não é pdf".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());

        sendProof(session, "comprovante.pdf", pdf()).andExpect(status().isOk());
        sendProof(session, "outro.pdf", pdf())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROOF_ALREADY_SUBMITTED"));
    }

    @Test
    void rejectedProofCanBeSentAgainAndTheReasonIsShown() throws Exception {
        CreatedOrder order = fixtures.acceptedOrder(catalog);
        Cookie session = client(order);
        sendProof(session, "comprovante.pdf", pdf());

        adminPost("/api/admin/payments/" + pendingPaymentId(order.id()) + "/reject", "{\"reason\":\"Valor não identificado\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NONE"));

        mockMvc.perform(get("/api/tracking/order").cookie(session))
                .andExpect(jsonPath("$.payment.payments[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.payment.payments[0].reviewNote").value("Valor não identificado"))
                .andExpect(jsonPath("$.payment.canSubmitProof").value(true));
        sendProof(session, "novo.pdf", pdf()).andExpect(status().isOk());
        assertThat(commercialStatus(order.id())).isEqualTo("AWAITING_DEPOSIT");
    }

    @Test
    void confirmationIsUndoneWhenTheSlotIsFull() throws Exception {
        CreatedOrder order = fixtures.acceptedOrder(catalog);
        sendProof(client(order), "comprovante.pdf", pdf());
        // Outros três pedidos ocupam todas as vagas do horário antes da confirmação.
        for (int i = 0; i < 3; i++) {
            long other = fixtures.create(catalog).id();
            tx.executeWithoutResult(s -> reservations.reserve(other, OrderFixtures.TUESDAY, OrderFixtures.TWO_PM));
        }
        long paymentId = pendingPaymentId(order.id());

        adminPost("/api/admin/payments/" + paymentId + "/confirm", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SLOT_FULL"));

        assertThat(jdbc.queryForObject("select status from payment where id = ?", String.class, paymentId))
                .as("nada é gravado se o horário estiver lotado").isEqualTo("PENDING_REVIEW");
        assertThat(commercialStatus(order.id())).isEqualTo("AWAITING_DEPOSIT");
    }

    @Test
    void partialDepositNeedsExplicitAcceptanceAndBalanceIsPaidAtPickup() throws Exception {
        CreatedOrder order = fixtures.acceptedOrder(catalog);
        String base = "/api/admin/orders/" + order.id() + "/payments";

        adminPost(base, "{\"kind\":\"DEPOSIT\",\"method\":\"CASH\",\"amount\":\"50.00\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value("50.00"))
                .andExpect(jsonPath("$.status").value("NONE"));
        assertThat(commercialStatus(order.id())).isEqualTo("AWAITING_DEPOSIT");

        adminPost(base, "{\"kind\":\"DEPOSIT\",\"method\":\"PIX\",\"amount\":\"10.00\",\"acceptAsDeposit\":true}")
                .andExpect(status().isOk());
        assertThat(commercialStatus(order.id())).isEqualTo("CONFIRMED");

        adminPost(base, "{\"kind\":\"BALANCE\",\"method\":\"CASH\",\"amount\":\"105.00\",\"notes\":\"Pago na retirada\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value("165.00"))
                .andExpect(jsonPath("$.balanceDue").value("0.00"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payments.length()").value(3));
    }

    @Test
    void refundsAreRegisteredWithoutDeletingPayments() throws Exception {
        CreatedOrder order = fixtures.acceptedOrder(catalog);
        String base = "/api/admin/orders/" + order.id() + "/payments";
        adminPost(base, "{\"kind\":\"DEPOSIT\",\"method\":\"PIX\",\"amount\":\"82.50\"}").andExpect(status().isOk());
        JsonNode detail = json.readTree(mockMvc.perform(get("/api/admin/orders/" + order.id()).cookie(admin))
                .andReturn().getResponse().getContentAsString());
        assertThat(detail.get("payments").get("status").asText()).isEqualTo("DEPOSIT_PAID");

        adminPost("/api/admin/orders/" + order.id() + "/cancel",
                "{\"reason\":\"Cliente desistiu\",\"version\":" + detail.get("version").asLong() + "}").andExpect(status().isOk());
        adminPost(base, "{\"kind\":\"REFUND\",\"method\":\"PIX\",\"amount\":\"100.00\"}")
                .andExpect(status().isBadRequest());
        adminPost(base, "{\"kind\":\"REFUND\",\"method\":\"PIX\",\"amount\":\"82.50\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value("0.00"))
                .andExpect(jsonPath("$.payments.length()").value(2));
    }

    @Test
    void paymentsCannotBeDeletedOrChangedAfterReviewInTheDatabase() throws Exception {
        CreatedOrder order = fixtures.acceptedOrder(catalog);
        adminPost("/api/admin/orders/" + order.id() + "/payments", "{\"kind\":\"DEPOSIT\",\"method\":\"PIX\",\"amount\":\"82.50\"}");
        long paymentId = jdbc.queryForObject("select id from payment where order_id = ?", Long.class, order.id());

        assertThatThrownBy(() -> jdbc.update("delete from payment where id = ?", paymentId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update payment set amount = 1 where id = ?", paymentId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update payment set status = 'REJECTED' where id = ?", paymentId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void paymentsRequireAnAcceptedQuoteAndAdminLogin() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        adminPost("/api/admin/orders/" + order.id() + "/payments", "{\"kind\":\"DEPOSIT\",\"method\":\"PIX\",\"amount\":\"10\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_ACCEPTED_QUOTE"));
        mockMvc.perform(get("/api/admin/orders/" + order.id() + "/payments")).andExpect(status().isUnauthorized());
    }

    @Test
    void clientCanOnlyDownloadFilesOfTheirOwnOrder() throws Exception {
        CreatedOrder mine = fixtures.acceptedOrder(catalog);
        CreatedOrder other = fixtures.acceptedOrder(catalog);
        sendProof(client(other), "comprovante.pdf", pdf());
        long othersFile = jdbc.queryForObject("select proof_file_id from payment where order_id = ?", Long.class, other.id());

        mockMvc.perform(get("/api/tracking/order/files/" + othersFile).cookie(client(mine)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tracking/order/files/" + othersFile).cookie(client(other)))
                .andExpect(status().isOk());
    }
}
