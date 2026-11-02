package br.com.confeitaria.modules.deadline;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import br.com.confeitaria.modules.notification.entity.NotificationType;
import br.com.confeitaria.modules.notification.service.NotificationService;
import br.com.confeitaria.modules.payment.service.PaymentService;
import br.com.confeitaria.support.IntegrationTest;
import br.com.confeitaria.support.MutableClock;
import br.com.confeitaria.support.OrderFixtures;
import br.com.confeitaria.support.OrderFixtures.Catalog;
import br.com.confeitaria.support.OrderFixtures.CreatedOrder;
import br.com.confeitaria.support.RecordingMailSender;

/**
 * Prazos: orçamento com validade de 48 h e aviso 12 h antes; sinal com prazo de 48 h após o aceite.
 * O relógio é avançado manualmente e o processo é chamado diretamente (o agendador fica desligado nos testes).
 */
@IntegrationTest
class DeadlineIntegrationTests {

    @Autowired
    private DeadlineService deadlines;

    @Autowired
    private OrderFixtures fixtures;

    @Autowired
    private PaymentService payments;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private MutableClock clock;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private JdbcTemplate jdbc;

    private Catalog catalog;

    @BeforeEach
    void setUp() {
        catalog = fixtures.prepare();
        jdbc.update("update bakery_settings set owner_notification_email = 'avisos@confeitaria.test'");
        mail.clear();
    }

    private String commercialStatus(long orderId) {
        return jdbc.queryForObject("select commercial_status from cake_order where id = ?", String.class, orderId);
    }

    private int notificationsOf(String type, long orderId) {
        return jdbc.queryForObject("select count(*) from notification where type = ? and order_id = ?", Integer.class,
                type, orderId);
    }

    @Test
    void quoteNearExpiryWarnsTheOwnerOnceEvenIfTheJobRunsTwice() throws Exception {
        CreatedOrder order = fixtures.create(catalog);
        fixtures.sendQuote(order.id());

        deadlines.runAll();
        assertThat(notificationsOf("QUOTE_EXPIRING", order.id())).as("ainda longe do vencimento").isZero();

        clock.advance(Duration.ofHours(37));
        deadlines.runAll();
        deadlines.runAll();

        assertThat(notificationsOf("QUOTE_EXPIRING", order.id())).isEqualTo(1);
        assertThat(commercialStatus(order.id())).isEqualTo("QUOTE_SENT");
        var emails = mail.awaitMessages(1, Duration.ofSeconds(5));
        assertThat(emails).anySatisfy(m -> {
            assertThat(m.getTo()).containsExactly("avisos@confeitaria.test");
            assertThat(m.getSubject()).isEqualTo("Orçamento perto de vencer");
            assertThat(m.getText()).contains("/admin/pedidos/" + order.id());
        });
    }

    @Test
    void expiredQuoteCancelsTheOrderIdempotently() {
        CreatedOrder order = fixtures.create(catalog);
        long quoteId = fixtures.sendQuote(order.id());

        clock.advance(Duration.ofHours(48));
        DeadlineService.RunResult first = deadlines.runAll();
        DeadlineService.RunResult second = deadlines.runAll();

        assertThat(first.quotesExpired()).isEqualTo(1);
        assertThat(second.quotesExpired()).isZero();
        assertThat(jdbc.queryForObject("select status from quote where id = ?", String.class, quoteId)).isEqualTo("EXPIRED");
        var row = jdbc.queryForMap("select commercial_status, cancelled_by, cancellation_reason, cancelled_at from cake_order where id = ?",
                order.id());
        assertThat(row).containsEntry("commercial_status", "CANCELLED").containsEntry("cancelled_by", "SYSTEM");
        assertThat((String) row.get("cancellation_reason")).contains("vencido");
        assertThat(row.get("cancelled_at")).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from order_history where order_id = ? and event_type = 'QUOTE_EXPIRED'",
                Integer.class, order.id())).isEqualTo(1);
        assertThat(notificationsOf("QUOTE_EXPIRED", order.id())).isEqualTo(1);
    }

    @Test
    void acceptedQuotesAreNotExpired() {
        CreatedOrder order = fixtures.acceptedOrder(catalog);
        jdbc.update("update bakery_settings set deposit_deadline_hours = null");

        clock.advance(Duration.ofDays(5));
        deadlines.runAll();

        assertThat(commercialStatus(order.id())).isEqualTo("AWAITING_DEPOSIT");
    }

    @Test
    void unpaidDepositCancelsAfterTheDeadlineWithAWarningBefore() {
        CreatedOrder order = fixtures.acceptedOrder(catalog);

        clock.advance(Duration.ofHours(37));
        deadlines.runAll();
        assertThat(notificationsOf("DEPOSIT_DEADLINE_NEAR", order.id())).isEqualTo(1);
        assertThat(commercialStatus(order.id())).isEqualTo("AWAITING_DEPOSIT");

        clock.advance(Duration.ofHours(12));
        deadlines.runAll();
        deadlines.runAll();
        assertThat(commercialStatus(order.id())).isEqualTo("CANCELLED");
        assertThat(notificationsOf("DEPOSIT_DEADLINE_PASSED", order.id())).isEqualTo(1);
    }

    @Test
    void proofUnderReviewPreventsAutomaticCancellation() {
        CreatedOrder order = fixtures.acceptedOrder(catalog);
        payments.submitProof(order.id(), new MockMultipartFile("proof", "c.pdf", "application/pdf",
                "%PDF-1.7 x".getBytes()), new BigDecimal("82.50"));

        clock.advance(Duration.ofHours(49));
        deadlines.runAll();

        assertThat(commercialStatus(order.id())).isEqualTo("AWAITING_DEPOSIT");
        assertThat(notificationsOf("DEPOSIT_DEADLINE_PASSED", order.id())).isEqualTo(1);
        assertThat(notificationsOf("DEPOSIT_DEADLINE_NEAR", order.id())).as("sem aviso antecipado com comprovante").isZero();
    }

    @Test
    void depositDeadlineCanBeDisabled() {
        jdbc.update("update bakery_settings set deposit_deadline_hours = null");
        CreatedOrder order = fixtures.acceptedOrder(catalog);

        clock.advance(Duration.ofDays(10));
        deadlines.runAll();

        assertThat(commercialStatus(order.id())).isEqualTo("AWAITING_DEPOSIT");
    }

    @Test
    void failedEmailsAreRetriedByTheJob() throws Exception {
        mail.setFailing(true);
        long id = jdbc.queryForObject("select id from cake_order order by id desc limit 1", Long.class);
        notifications.notify(NotificationType.NEW_ORDER, id, "Teste de reenvio", "TEST_RETRY:" + System.nanoTime());
        Thread.sleep(500);
        assertThat(jdbc.queryForObject("select email_status from notification where message = 'Teste de reenvio'",
                String.class)).isEqualTo("FAILED");

        mail.setFailing(false);
        clock.advance(Duration.ofMinutes(2));
        deadlines.runAll();

        assertThat(jdbc.queryForObject("select email_status from notification where message = 'Teste de reenvio'",
                String.class)).isEqualTo("SENT");
        assertThat(mail.sentWithSubject("Nova encomenda")).isNotEmpty();
    }
}
