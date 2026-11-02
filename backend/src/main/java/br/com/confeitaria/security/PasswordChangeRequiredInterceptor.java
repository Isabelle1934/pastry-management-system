package br.com.confeitaria.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import br.com.confeitaria.common.exception.ApiException;
import br.com.confeitaria.modules.auth.repository.AdminUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Enquanto a senha provisória não for trocada, bloqueia o painel (exceto /api/admin/auth/**).
 * Consulta o banco a cada requisição: a sinalização muda sem precisar de novo login.
 */
@Component
public class PasswordChangeRequiredInterceptor implements HandlerInterceptor {

    public static final String CODE = "PASSWORD_CHANGE_REQUIRED";

    private final AdminUserRepository repository;

    public PasswordChangeRequiredInterceptor(AdminUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AdminPrincipal principal) {
            boolean mustChange = repository.findById(principal.getId())
                    .map(user -> user.isMustChangePassword())
                    .orElse(false);
            if (mustChange) {
                throw new PasswordChangeRequiredException();
            }
        }
        return true;
    }

    static final class PasswordChangeRequiredException extends ApiException {

        PasswordChangeRequiredException() {
            super(HttpStatus.FORBIDDEN, CODE, "Defina uma nova senha para continuar usando o painel.");
        }
    }
}
