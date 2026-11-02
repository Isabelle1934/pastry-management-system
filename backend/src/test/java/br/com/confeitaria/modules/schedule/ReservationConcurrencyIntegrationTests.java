package br.com.confeitaria.modules.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.confeitaria.common.exception.BusinessRuleException;
import br.com.confeitaria.modules.schedule.service.ReservationService;
import br.com.confeitaria.support.IntegrationTest;
import br.com.confeitaria.support.MutableClock;

/**
 * Garante a regra crítica da agenda: confirmações simultâneas nunca ultrapassam o limite do horário.
 * Cada "confirmação" roda em sua própria transação e thread, contra o PostgreSQL real.
 */
@IntegrationTest
class ReservationConcurrencyIntegrationTests {

    /** Terça-feira. */
    private static final LocalDate DATE = LocalDate.of(2026, 11, 10);
    private static final LocalTime TIME = LocalTime.of(14, 0);

    @Autowired
    private ReservationService reservations;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private ScheduleTestSupport schedule;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-11-02T12:00:00Z"));
        schedule = new ScheduleTestSupport(jdbc);
        schedule.reset(24, 90);
    }

    private long newOrderId() {
        return schedule.newOrder();
    }

    private void reserve(long orderId, LocalDate date, LocalTime time) {
        tx.executeWithoutResult(status -> reservations.reserve(orderId, date, time));
    }

    private static String errorCode(Throwable error) {
        return error instanceof BusinessRuleException business ? business.getCode() : error.getClass().getSimpleName();
    }

    /** Dispara as tarefas ao mesmo tempo (latch) e devolve o resultado de cada uma ("OK" ou o código do erro). */
    private static List<String> runConcurrently(int threads, Callable<Void> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    task.call();
                    return "OK";
                } catch (Exception e) {
                    return errorCode(e);
                }
            }));
        }
        start.countDown();
        List<String> results = new ArrayList<>();
        for (Future<String> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }

    @Test
    void simultaneousConfirmationsNeverExceedTheSlotCapacity() throws Exception {
        schedule.slot(2, "14:00", 3, true);

        java.util.concurrent.ConcurrentLinkedQueue<Long> orderIds = new java.util.concurrent.ConcurrentLinkedQueue<>();
        for (int i = 0; i < 20; i++) {
            orderIds.add(newOrderId());
        }

        List<String> results = runConcurrently(20, () -> {
            reserve(orderIds.poll(), DATE, TIME);
            return null;
        });

        assertThat(results).filteredOn("OK"::equals).hasSize(3);
        assertThat(results).filteredOn(ReservationService.SLOT_FULL::equals).hasSize(17);
        assertThat(schedule.reserved(DATE, "14:00")).isEqualTo(3);
        assertThat(schedule.activeReservations(DATE, "14:00")).isEqualTo(3);
    }

    @Test
    void theSameOrderConfirmedTwiceAtOnceOccupiesASingleSpot() throws Exception {
        schedule.slot(2, "14:00", 5, true);
        long orderId = newOrderId();

        List<String> results = runConcurrently(8, () -> {
            reserve(orderId, DATE, TIME);
            return null;
        });

        assertThat(results).contains("OK");
        assertThat(schedule.reserved(DATE, "14:00")).isEqualTo(1);
        assertThat(schedule.activeReservations(DATE, "14:00")).isEqualTo(1);
    }

    @Test
    void releasingFreesTheSpotAndIsIdempotent() {
        schedule.slot(2, "14:00", 1, true);
        long first = newOrderId();
        long second = newOrderId();
        reserve(first, DATE, TIME);

        assertThatThrownBy(() -> reserve(second, DATE, TIME))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode()).isEqualTo(ReservationService.SLOT_FULL);

        Boolean releasedFirstTime = tx.execute(status -> reservations.release(first, "Pedido cancelado"));
        Boolean releasedAgain = tx.execute(status -> reservations.release(first, "Pedido cancelado"));
        assertThat(releasedFirstTime).isTrue();
        assertThat(releasedAgain).isFalse();
        assertThat(schedule.reserved(DATE, "14:00")).isZero();

        reserve(second, DATE, TIME);
        assertThat(schedule.reserved(DATE, "14:00")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select release_reason from slot_reservation where order_id = ?",
                String.class, first)).isEqualTo("Pedido cancelado");
    }

    @Test
    void reservingAgainIsIdempotentAndRescheduleMovesTheSpot() {
        schedule.slot(2, "14:00", 2, true);
        schedule.slot(2, "16:00", 2, true);
        long orderId = newOrderId();

        reserve(orderId, DATE, TIME);
        reserve(orderId, DATE, TIME);
        assertThat(schedule.reserved(DATE, "14:00")).isEqualTo(1);

        reserve(orderId, DATE, LocalTime.of(16, 0));
        assertThat(schedule.reserved(DATE, "14:00")).isZero();
        assertThat(schedule.reserved(DATE, "16:00")).isEqualTo(1);
        assertThat(schedule.activeReservations(DATE, "16:00")).isEqualTo(1);
    }

    @Test
    void failedRescheduleKeepsTheOriginalSpot() {
        schedule.slot(2, "14:00", 2, true);
        schedule.slot(2, "16:00", 1, true);
        long orderId = newOrderId();
        reserve(orderId, DATE, TIME);
        reserve(newOrderId(), DATE, LocalTime.of(16, 0));

        assertThatThrownBy(() -> reserve(orderId, DATE, LocalTime.of(16, 0))).isInstanceOf(BusinessRuleException.class);

        assertThat(schedule.reserved(DATE, "14:00")).isEqualTo(1);
        assertThat(schedule.activeReservations(DATE, "14:00")).isEqualTo(1);
    }

    @Test
    void blockedInactiveOrUnknownSlotsCannotBeReserved() {
        schedule.slot(2, "14:00", 2, true);
        schedule.slot(2, "16:00", 2, false);
        schedule.block(DATE.plusWeeks(1), null);

        assertThatThrownBy(() -> reserve(newOrderId(), DATE, LocalTime.of(16, 0)))
                .extracting(ReservationConcurrencyIntegrationTests::errorCode).isEqualTo(ReservationService.SLOT_UNAVAILABLE);
        assertThatThrownBy(() -> reserve(newOrderId(), DATE, LocalTime.of(9, 0)))
                .extracting(ReservationConcurrencyIntegrationTests::errorCode).isEqualTo(ReservationService.SLOT_UNAVAILABLE);
        assertThatThrownBy(() -> reserve(newOrderId(), DATE.plusWeeks(1), TIME))
                .extracting(ReservationConcurrencyIntegrationTests::errorCode).isEqualTo(ReservationService.SLOT_UNAVAILABLE);
    }

    @Test
    void loweringCapacityKeepsExistingReservationsButBlocksNewOnes() {
        schedule.slot(2, "14:00", 3, true);
        reserve(newOrderId(), DATE, TIME);
        reserve(newOrderId(), DATE, TIME);
        jdbc.update("update weekly_slot set capacity = 1");

        assertThatThrownBy(() -> reserve(newOrderId(), DATE, TIME))
                .extracting(ReservationConcurrencyIntegrationTests::errorCode).isEqualTo(ReservationService.SLOT_FULL);
        assertThat(schedule.activeReservations(DATE, "14:00")).isEqualTo(2);
    }

    @Test
    void reservingRequiresTheCallersTransaction() {
        schedule.slot(2, "14:00", 1, true);
        assertThatThrownBy(() -> reservations.reserve(newOrderId(), DATE, TIME))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
}
