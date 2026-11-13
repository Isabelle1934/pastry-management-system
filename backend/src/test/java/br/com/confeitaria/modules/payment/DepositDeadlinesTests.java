package br.com.confeitaria.modules.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import br.com.confeitaria.modules.payment.service.DepositDeadlines;

class DepositDeadlinesTests {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final Instant ACCEPTED = Instant.parse("2026-11-02T12:00:00Z");
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 6);
    private static final LocalTime TWO_PM = LocalTime.of(14, 0);

    @Test
    void earliestLimitWins() {
        // 48 h após o aceite (04/11 12:00) vence antes de 24 h antes da entrega (05/11 17:00 UTC).
        assertThat(DepositDeadlines.dueAt(ACCEPTED, 48, 24, FRIDAY, TWO_PM, SP))
                .isEqualTo(Instant.parse("2026-11-04T12:00:00Z"));
        assertThat(DepositDeadlines.dueAt(ACCEPTED, 120, 24, FRIDAY, TWO_PM, SP))
                .isEqualTo(Instant.parse("2026-11-05T17:00:00Z"));
    }

    @Test
    void eachLimitCanBeTurnedOff() {
        assertThat(DepositDeadlines.dueAt(ACCEPTED, null, 0, FRIDAY, TWO_PM, SP)).isNull();
        assertThat(DepositDeadlines.dueAt(ACCEPTED, null, 24, FRIDAY, TWO_PM, SP))
                .isEqualTo(Instant.parse("2026-11-05T17:00:00Z"));
        assertThat(DepositDeadlines.dueAt(ACCEPTED, 48, 0, FRIDAY, TWO_PM, SP))
                .isEqualTo(Instant.parse("2026-11-04T12:00:00Z"));
        assertThat(DepositDeadlines.dueAt(null, 48, 24, FRIDAY, TWO_PM, SP)).isNull();
    }

    @Test
    void acceptanceCloseToTheDateStillLeavesTwoHoursToPay() {
        // Aceite 20 h antes da retirada: o limite de 24 h já passou, então a cliente tem 2 h.
        Instant lateAcceptance = Instant.parse("2026-11-05T21:00:00Z");
        assertThat(DepositDeadlines.dueAt(lateAcceptance, 48, 24, FRIDAY, TWO_PM, SP))
                .isEqualTo(Instant.parse("2026-11-05T23:00:00Z"));
        // O prazo após o aceite configurado em 1 h continua valendo.
        assertThat(DepositDeadlines.dueAt(lateAcceptance, 1, 24, FRIDAY, TWO_PM, SP))
                .isEqualTo(Instant.parse("2026-11-05T22:00:00Z"));
    }
}
