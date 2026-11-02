package br.com.confeitaria.common.text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatação em português para mensagens lidas pela proprietária (avisos, e-mails e histórico).
 * A API continua devolvendo valores em formato técnico ("82.50", "2026-12-04").
 */
public final class Texts {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Texts() {
    }

    /** 1234.5 → "R$ 1.234,50" */
    public static String brl(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
        return "R$ " + format.format(value.setScale(2, RoundingMode.HALF_UP));
    }

    /** 2026-12-04 → "04/12/2026" */
    public static String date(LocalDate value) {
        return DATE.format(value);
    }
}
