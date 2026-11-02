package br.com.confeitaria.modules.auth.service;

import java.time.Duration;

/** Publicado dentro da transação; o e-mail só é enviado após o commit. Nunca registre {@code rawToken}. */
public record PasswordResetRequestedEvent(String email, String name, String rawToken, Duration validity) {

    @Override
    public String toString() {
        return "PasswordResetRequestedEvent[validity=" + validity + "]";
    }
}
