package br.com.confeitaria.modules.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.audit.AuditService;
import br.com.confeitaria.common.exception.ApiException;
import br.com.confeitaria.common.exception.NotFoundException;
import br.com.confeitaria.modules.auth.dto.AdminMeResponse;
import br.com.confeitaria.modules.auth.entity.AdminUser;
import br.com.confeitaria.modules.auth.repository.AdminUserRepository;
import br.com.confeitaria.security.AdminPrincipal;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

    private final AuthenticationManager authenticationManager;
    private final AuthRateLimiter rateLimiter;
    private final AdminUserRepository users;
    private final AuditService audit;

    public AuthService(AuthenticationManager authenticationManager, AuthRateLimiter rateLimiter,
            AdminUserRepository users, AuditService audit) {
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
        this.users = users;
        this.audit = audit;
    }

    /**
     * Verifica e-mail e senha. A mesma resposta é dada para e-mail inexistente, senha errada
     * ou conta desativada, para não revelar quais e-mails estão cadastrados.
     */
    @Transactional
    public Authentication authenticate(String email, String password, String ip) {
        String normalized = AdminUser.normalizeEmail(email);
        rateLimiter.checkLoginAllowed(normalized, ip);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalized, password));
            rateLimiter.recordLoginSuccess(normalized);
            AdminPrincipal principal = (AdminPrincipal) authentication.getPrincipal();
            audit.record(ActorType.OWNER, principal.getId(), "LOGIN_SUCCEEDED", "admin_user", principal.getId(), null);
            return authentication;
        } catch (AuthenticationException e) {
            rateLimiter.recordLoginFailure(normalized, ip);
            log.info("Tentativa de login malsucedida ({})", e.getClass().getSimpleName());
            throw new InvalidCredentialsException();
        }
    }

    @Transactional(readOnly = true)
    public AdminMeResponse me(Long adminUserId) {
        AdminUser user = users.findById(adminUserId)
                .orElseThrow(() -> new NotFoundException("Usuária não encontrada."));
        return AdminMeResponse.from(user);
    }

    static final class InvalidCredentialsException extends ApiException {

        InvalidCredentialsException() {
            super(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS, "E-mail ou senha incorretos.");
        }
    }
}
