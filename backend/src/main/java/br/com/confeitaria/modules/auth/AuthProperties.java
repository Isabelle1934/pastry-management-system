package br.com.confeitaria.modules.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configurações de autenticação ({@code app.auth.*}).
 *
 * @param publicBaseUrl     endereço público do site, usado no link de redefinição de senha
 * @param cookieSecure      marca os cookies como Secure (obrigatório em produção, HTTPS)
 * @param passwordResetTtl  validade do link de redefinição de senha
 * @param bootstrap         conta administrativa inicial, criada apenas se não existir nenhuma
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String publicBaseUrl,
        @DefaultValue("true") boolean cookieSecure,
        @DefaultValue("30m") Duration passwordResetTtl,
        @DefaultValue RateLimits rateLimits,
        @DefaultValue Bootstrap bootstrap) {

    public record RateLimits(
            @DefaultValue("5") int loginFailuresPerEmail,
            @DefaultValue("20") int loginFailuresPerIp,
            @DefaultValue("15m") Duration loginWindow,
            @DefaultValue("3") int resetRequestsPerEmail,
            @DefaultValue("10") int resetRequestsPerIp,
            @DefaultValue("1h") Duration resetRequestWindow,
            @DefaultValue("10") int resetAttemptsPerIp,
            @DefaultValue("15m") Duration resetAttemptWindow) {
    }

    public record Bootstrap(String email, String name, String password) {

        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
