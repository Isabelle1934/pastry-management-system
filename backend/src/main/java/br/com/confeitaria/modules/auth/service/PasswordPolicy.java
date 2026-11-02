package br.com.confeitaria.modules.auth.service;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import br.com.confeitaria.common.exception.FieldValidationException;

/**
 * Regras de senha: comprimento (10–64) em vez de exigências de símbolos, como recomenda o NIST.
 * O limite de 72 bytes vem do BCrypt, que ignora o que passar disso.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 64;
    private static final int BCRYPT_MAX_BYTES = 72;

    public void validate(String password, String email, String field) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new FieldValidationException(field, "A senha deve ter pelo menos " + MIN_LENGTH + " caracteres.");
        }
        if (password.codePointCount(0, password.length()) > MAX_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new FieldValidationException(field, "A senha deve ter no máximo " + MAX_LENGTH + " caracteres.");
        }
        if (password.isBlank() || password.chars().distinct().count() == 1) {
            throw new FieldValidationException(field, "Escolha uma senha menos previsível.");
        }
        if (email != null && password.trim().equalsIgnoreCase(email.trim())) {
            throw new FieldValidationException(field, "A senha não pode ser igual ao e-mail.");
        }
    }
}
