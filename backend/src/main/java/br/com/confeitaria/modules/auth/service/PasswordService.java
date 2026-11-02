package br.com.confeitaria.modules.auth.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.audit.AuditService;
import br.com.confeitaria.common.exception.ApiException;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.common.exception.NotFoundException;
import br.com.confeitaria.modules.auth.AuthProperties;
import br.com.confeitaria.modules.auth.entity.AdminUser;
import br.com.confeitaria.modules.auth.entity.PasswordResetToken;
import br.com.confeitaria.modules.auth.repository.AdminUserRepository;
import br.com.confeitaria.modules.auth.repository.PasswordResetTokenRepository;
import br.com.confeitaria.security.SessionRevoker;

/** Recuperação de senha por e-mail e troca de senha pela usuária logada. */
@Service
public class PasswordService {

    public static final String INVALID_RESET_TOKEN = "INVALID_RESET_TOKEN";

    private final AdminUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy policy;
    private final AuthRateLimiter rateLimiter;
    private final SessionRevoker sessionRevoker;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final AuthProperties properties;
    private final Clock clock;

    public PasswordService(AdminUserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder passwordEncoder,
            PasswordPolicy policy, AuthRateLimiter rateLimiter, SessionRevoker sessionRevoker,
            ApplicationEventPublisher events, AuditService audit, AuthProperties properties, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.policy = policy;
        this.rateLimiter = rateLimiter;
        this.sessionRevoker = sessionRevoker;
        this.events = events;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Gera um link de redefinição e o envia por e-mail (após o commit, de forma assíncrona).
     * Não informa se o e-mail existe: quem chama sempre responde da mesma forma.
     */
    @Transactional
    public void requestReset(String email, String ip) {
        String normalized = AdminUser.normalizeEmail(email);
        if (!rateLimiter.allowResetRequest(normalized, ip)) {
            return;
        }
        users.findByEmail(normalized).filter(AdminUser::isEnabled).ifPresent(user -> {
            Instant now = clock.instant();
            tokens.invalidateActiveTokens(user.getId(), now);
            String rawToken = ResetTokens.generate();
            Instant expiresAt = now.plus(properties.passwordResetTtl());
            tokens.save(new PasswordResetToken(user, ResetTokens.hash(rawToken), now, expiresAt));
            audit.record(ActorType.SYSTEM, null, "PASSWORD_RESET_REQUESTED", "admin_user", user.getId(), null);
            events.publishEvent(new PasswordResetRequestedEvent(user.getEmail(), user.getName(), rawToken,
                    properties.passwordResetTtl()));
        });
    }

    /** Redefine a senha com o token do e-mail. Token de uso único; todas as sessões são encerradas. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword, String ip) {
        rateLimiter.checkResetAttempt(ip);
        Instant now = clock.instant();
        PasswordResetToken token = tokens.findByTokenHashForUpdate(ResetTokens.hash(rawToken))
                .filter(t -> t.isUsable(now))
                .filter(t -> t.getAdminUser().isEnabled())
                .orElseThrow(InvalidResetTokenException::new);

        AdminUser user = token.getAdminUser();
        policy.validate(newPassword, user.getEmail(), "newPassword");

        token.markUsed(now);
        user.changePassword(passwordEncoder.encode(newPassword), now);
        tokens.invalidateActiveTokens(user.getId(), now);
        audit.record(ActorType.OWNER, user.getId(), "PASSWORD_RESET", "admin_user", user.getId(), null);
        sessionRevoker.revokeAll(user.getEmail(), null);
    }

    /** Troca de senha pela usuária logada. Mantém a sessão atual e encerra as demais. */
    @Transactional
    public void changePassword(Long adminUserId, String currentPassword, String newPassword, String currentSessionId) {
        AdminUser user = users.findById(adminUserId)
                .orElseThrow(() -> new NotFoundException("Usuária não encontrada."));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new FieldValidationException("currentPassword", "Senha atual incorreta.");
        }
        if (currentPassword.equals(newPassword)) {
            throw new FieldValidationException("newPassword", "A nova senha deve ser diferente da atual.");
        }
        policy.validate(newPassword, user.getEmail(), "newPassword");

        Instant now = clock.instant();
        user.changePassword(passwordEncoder.encode(newPassword), now);
        tokens.invalidateActiveTokens(user.getId(), now);
        audit.record(ActorType.OWNER, user.getId(), "PASSWORD_CHANGED", "admin_user", user.getId(), null);
        sessionRevoker.revokeAll(user.getEmail(), currentSessionId);
    }

    static final class InvalidResetTokenException extends ApiException {

        InvalidResetTokenException() {
            super(HttpStatus.BAD_REQUEST, INVALID_RESET_TOKEN,
                    "Este link de redefinição é inválido ou expirou. Solicite um novo.");
        }
    }
}
