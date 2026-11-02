package br.com.confeitaria.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import br.com.confeitaria.common.exception.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Respostas 401/403 dos filtros de segurança no mesmo formato ProblemDetail do restante da API
 * (sem redirecionamento para página de login e sem header WWW-Authenticate).
 */
@Component
class ProblemResponseWriter {

    static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    static final String ACCESS_DENIED = "ACCESS_DENIED";
    static final String CSRF_INVALID = "CSRF_INVALID";

    private final JsonMapper jsonMapper;

    ProblemResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> write(response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED,
                "Sua sessão expirou ou você não está conectada. Entre novamente.");
    }

    AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            if (ex instanceof CsrfException) {
                write(response, HttpStatus.FORBIDDEN, CSRF_INVALID,
                        "Não foi possível validar a requisição. Recarregue a página e tente novamente.");
            } else {
                write(response, HttpStatus.FORBIDDEN, ACCESS_DENIED, "Acesso não permitido.");
            }
        };
    }

    private void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), ProblemDetailFactory.create(status, code, message));
    }
}
