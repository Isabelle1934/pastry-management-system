package br.com.confeitaria.common.exception;

import org.springframework.http.HttpStatus;

/** Erro de validação de um campo detectado no serviço (ex.: política de senha). Resposta 400 com {@code errors[]}. */
public class FieldValidationException extends ApiException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
