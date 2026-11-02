package br.com.confeitaria.modules.order.service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;

/**
 * Códigos de acompanhamento: 16 caracteres do alfabeto Crockford Base32 (sem I, L, O, U),
 * gerados com {@link SecureRandom} — 80 bits, inviável de adivinhar. Exibidos como XXXX-XXXX-XXXX-XXXX.
 */
public final class TrackingCodes {

    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    static final int LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TrackingCodes() {
    }

    public static String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** "ABCD1234EFGH5678" → "ABCD-1234-EFGH-5678" */
    public static String format(String code) {
        return code.substring(0, 4) + "-" + code.substring(4, 8) + "-" + code.substring(8, 12) + "-" + code.substring(12);
    }

    /**
     * Interpreta o que a cliente digitou: ignora espaços e hífens, aceita minúsculas e corrige
     * confusões comuns (O→0, I/L→1), como define o Crockford Base32.
     */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String code = input.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT)
                .replace('O', '0').replace('I', '1').replace('L', '1');
        if (code.length() != LENGTH || code.chars().anyMatch(c -> ALPHABET.indexOf(c) < 0)) {
            return Optional.empty();
        }
        return Optional.of(code);
    }
}
