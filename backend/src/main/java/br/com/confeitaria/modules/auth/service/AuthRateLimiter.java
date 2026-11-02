package br.com.confeitaria.modules.auth.service;

import java.time.Clock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.confeitaria.common.exception.TooManyRequestsException;
import br.com.confeitaria.common.ratelimit.FixedWindowCounter;
import br.com.confeitaria.modules.auth.AuthProperties;

/** Limites de tentativas de login e de redefinição de senha, por e-mail e por IP. */
@Component
public class AuthRateLimiter {

    private final FixedWindowCounter loginFailuresByEmail;
    private final FixedWindowCounter loginFailuresByIp;
    private final FixedWindowCounter resetRequestsByEmail;
    private final FixedWindowCounter resetRequestsByIp;
    private final FixedWindowCounter resetAttemptsByIp;

    public AuthRateLimiter(AuthProperties properties, Clock clock) {
        var limits = properties.rateLimits();
        this.loginFailuresByEmail = new FixedWindowCounter(limits.loginFailuresPerEmail(), limits.loginWindow(), clock);
        this.loginFailuresByIp = new FixedWindowCounter(limits.loginFailuresPerIp(), limits.loginWindow(), clock);
        this.resetRequestsByEmail = new FixedWindowCounter(limits.resetRequestsPerEmail(), limits.resetRequestWindow(), clock);
        this.resetRequestsByIp = new FixedWindowCounter(limits.resetRequestsPerIp(), limits.resetRequestWindow(), clock);
        this.resetAttemptsByIp = new FixedWindowCounter(limits.resetAttemptsPerIp(), limits.resetAttemptWindow(), clock);
    }

    /** Bloqueia antes mesmo de verificar a senha (inclusive se a senha estiver correta). */
    public void checkLoginAllowed(String email, String ip) {
        if (loginFailuresByEmail.isLimitReached(email) || loginFailuresByIp.isLimitReached(ip)) {
            throw new TooManyRequestsException();
        }
    }

    public void recordLoginFailure(String email, String ip) {
        loginFailuresByEmail.increment(email);
        loginFailuresByIp.increment(ip);
    }

    public void recordLoginSuccess(String email) {
        loginFailuresByEmail.reset(email);
    }

    /**
     * Excesso por IP retorna 429. Excesso por e-mail é silencioso ({@code false}): a resposta
     * continua igual para não revelar se o e-mail existe nem permitir inundar a caixa de entrada.
     */
    public boolean allowResetRequest(String email, String ip) {
        if (!resetRequestsByIp.tryAcquire(ip)) {
            throw new TooManyRequestsException();
        }
        return resetRequestsByEmail.tryAcquire(email);
    }

    public void checkResetAttempt(String ip) {
        if (!resetAttemptsByIp.tryAcquire(ip)) {
            throw new TooManyRequestsException();
        }
    }

    @Scheduled(fixedDelayString = "PT10M")
    void evictExpired() {
        loginFailuresByEmail.evictExpired();
        loginFailuresByIp.evictExpired();
        resetRequestsByEmail.evictExpired();
        resetRequestsByIp.evictExpired();
        resetAttemptsByIp.evictExpired();
    }

    /** Usado em testes para isolar cenários. */
    public void clear() {
        loginFailuresByEmail.clear();
        loginFailuresByIp.clear();
        resetRequestsByEmail.clear();
        resetRequestsByIp.clear();
        resetAttemptsByIp.clear();
    }
}
