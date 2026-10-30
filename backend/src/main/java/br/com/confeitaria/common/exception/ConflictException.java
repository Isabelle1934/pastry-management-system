package br.com.confeitaria.common.exception;

import org.springframework.http.HttpStatus;

/** Conflito com o estado atual do recurso (409), ex.: transição de status inválida. */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    public ConflictException(String message) {
        this(ErrorCode.CONFLICT, message);
    }
}
