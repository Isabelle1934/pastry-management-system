package br.com.confeitaria.modules.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.confeitaria.modules.order.service.TrackingCodes;

class TrackingCodesTests {

    @Test
    void generatesSixteenCrockfordCharactersWithoutRepetition() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String code = TrackingCodes.generate();
            assertThat(code).matches("[0-9A-HJKMNP-TV-Z]{16}");
            codes.add(code);
        }
        assertThat(codes).hasSize(10_000);
    }

    @Test
    void formatsInGroupsOfFour() {
        assertThat(TrackingCodes.format("ABCD1234EFGH5678")).isEqualTo("ABCD-1234-EFGH-5678");
    }

    @Test
    void normalizesWhatTheCustomerTypes() {
        assertThat(TrackingCodes.normalize(" abcd-1234-efgh-5678 ")).contains("ABCD1234EFGH5678");
        assertThat(TrackingCodes.normalize("ABCD 1234 EFGH 5678")).contains("ABCD1234EFGH5678");
        // Confusões comuns: O→0, I/L→1
        assertThat(TrackingCodes.normalize("OBCD-I234-EFGH-567L")).contains("0BCD1234EFGH5671");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ABCD-1234", "ABCD-1234-EFGH-5678-9", "ABCD-1234-EFGH-567U", "ABCD!1234EFGH5678"})
    void rejectsInvalidCodes(String input) {
        assertThat(TrackingCodes.normalize(input)).isEmpty();
    }
}
