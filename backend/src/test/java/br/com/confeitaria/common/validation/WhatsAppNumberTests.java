package br.com.confeitaria.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class WhatsAppNumberTests {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "(11) 99999-8888    | +5511999998888",
            "11999998888        | +5511999998888",
            "+55 11 99999-8888  | +5511999998888",
            "55 11 99999 8888   | +5511999998888",
            "011 99999-8888     | +5511999998888",
            "(21) 3456-7890     | +552134567890",
            "+55 (85) 98765-4321| +5585987654321",
    })
    void normalizesValidBrazilianNumbers(String input, String expected) {
        assertThat(WhatsAppNumber.normalize(input)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "abc", "9999-8888", "(11) 8999-88881", "(11) 89999-8888", "(01) 99999-8888",
            "(10) 99999-8888", "(11) 1234-5678", "+1 415 555 0100", "1199999888877",
    })
    void rejectsInvalidNumbers(String input) {
        assertThat(WhatsAppNumber.normalize(input)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "+5511999998888 | (11) 99999-8888",
            "+552134567890  | (21) 3456-7890",
    })
    void formatsForDisplay(String e164, String expected) {
        assertThat(WhatsAppNumber.format(e164)).isEqualTo(expected);
    }
}
