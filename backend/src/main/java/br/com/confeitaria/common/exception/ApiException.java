package br.com.confeitaria.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceção de domínio com status HTTP, código estável e mensagem segura para exibir ao usuário.
 * A mensagem nunca deve conter dados pessoais, credenciais ou detalhes internos.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
