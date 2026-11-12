package br.com.confeitaria.modules.finance.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import br.com.confeitaria.common.exception.FieldValidationException;

/** Período dos relatórios: padrão = mês atual; no máximo 366 dias. */
public final class Periods {

    public static final int MAX_DAYS = 366;

    public record Range(LocalDate from, LocalDate to) {
    }

    private Periods() {
    }

    public static Range resolve(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate start = from != null ? from : (to != null ? to.withDayOfMonth(1) : today.withDayOfMonth(1));
        LocalDate end = to != null ? to : start.withDayOfMonth(start.lengthOfMonth());
        if (end.isBefore(start)) {
            throw new FieldValidationException("to", "A data final deve ser igual ou posterior à inicial.");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
            throw new FieldValidationException("to", "Escolha um período de até 1 ano.");
        }
        return new Range(start, end);
    }
}
