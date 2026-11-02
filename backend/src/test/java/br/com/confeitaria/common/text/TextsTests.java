package br.com.confeitaria.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TextsTests {

    @Test
    void formatsMoneyInBrazilianStyle() {
        assertThat(Texts.brl(new BigDecimal("82.5"))).isEqualTo("R$ 82,50");
        assertThat(Texts.brl(new BigDecimal("1234.56"))).isEqualTo("R$ 1.234,56");
        assertThat(Texts.brl(new BigDecimal("0"))).isEqualTo("R$ 0,00");
        assertThat(Texts.brl(new BigDecimal("999999.99"))).isEqualTo("R$ 999.999,99");
    }

    @Test
    void formatsDatesAsDayMonthYear() {
        assertThat(Texts.date(LocalDate.of(2026, 12, 4))).isEqualTo("04/12/2026");
    }
}
