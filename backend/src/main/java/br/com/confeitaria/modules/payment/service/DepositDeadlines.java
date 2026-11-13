package br.com.confeitaria.modules.payment.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Prazo para pagar o sinal (BUSINESS_RULES.md §6). Vale o que vencer primeiro:
 * <ul>
 * <li>{@code depositDeadlineHours} horas depois do aceite do orçamento (vazio = sem esse limite);</li>
 * <li>{@code depositCutoffHours} horas antes da data/horário agendado (0 = sem esse limite).</li>
 * </ul>
 * Quando o orçamento é aceito muito perto da data, a cliente ainda tem pelo menos {@link #MIN_WINDOW} para
 * pagar — um prazo que já começa vencido cancelaria o pedido sem chance de pagamento.
 */
public final class DepositDeadlines {

    static final Duration MIN_WINDOW = Duration.ofHours(2);

    private DepositDeadlines() {
    }

    /** @return o fim do prazo, ou {@code null} quando nenhum limite está configurado */
    public static Instant dueAt(Instant acceptedAt, Integer deadlineHours, int cutoffHours, LocalDate date,
            LocalTime time, ZoneId zone) {
        if (acceptedAt == null) {
            return null;
        }
        Instant due = deadlineHours == null ? null : acceptedAt.plus(Duration.ofHours(deadlineHours));
        if (cutoffHours > 0 && date != null && time != null) {
            Instant cutoff = date.atTime(time).atZone(zone).toInstant().minus(Duration.ofHours(cutoffHours));
            Instant earliest = acceptedAt.plus(MIN_WINDOW);
            if (cutoff.isBefore(earliest)) {
                cutoff = earliest;
            }
            due = due == null || cutoff.isBefore(due) ? cutoff : due;
        }
        return due;
    }
}
