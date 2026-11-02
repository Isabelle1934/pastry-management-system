package br.com.confeitaria.common.validation;

import java.util.Optional;

/**
 * Números brasileiros de WhatsApp. Aceita entradas como "(11) 99999-8888", "11999998888",
 * "+55 11 99999-8888" ou "011 99999-8888" e normaliza para E.164: {@code +5511999998888}.
 * Aceita celular (11 dígitos com 9 inicial) e fixo (10 dígitos), pois empresas usam WhatsApp Business em fixo.
 */
public final class WhatsAppNumber {

    private WhatsAppNumber() {
    }

    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String digits = input.replaceAll("\\D", "");
        if (digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13)) {
            digits = digits.substring(2);
        } else if (digits.startsWith("0") && (digits.length() == 11 || digits.length() == 12)) {
            digits = digits.substring(1);
        }
        if (digits.length() != 10 && digits.length() != 11) {
            return Optional.empty();
        }
        if (digits.charAt(0) == '0' || digits.charAt(1) == '0') {
            return Optional.empty();
        }
        char firstLocalDigit = digits.charAt(2);
        if (digits.length() == 11 && firstLocalDigit != '9') {
            return Optional.empty();
        }
        if (digits.length() == 10 && (firstLocalDigit < '2' || firstLocalDigit > '5')) {
            return Optional.empty();
        }
        return Optional.of("+55" + digits);
    }

    /** {@code +5511999998888} → {@code (11) 99999-8888}. */
    public static String format(String e164) {
        if (e164 == null || !e164.startsWith("+55")) {
            return e164;
        }
        String local = e164.substring(3);
        String ddd = local.substring(0, 2);
        String number = local.substring(2);
        int split = number.length() - 4;
        return "(" + ddd + ") " + number.substring(0, split) + "-" + number.substring(split);
    }
}
