package br.com.confeitaria.common.exception;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends ApiException {

    public static final String CODE = "TOO_MANY_ATTEMPTS";

    public TooManyRequestsException() {
        super(HttpStatus.TOO_MANY_REQUESTS, CODE, "Muitas tentativas. Aguarde alguns minutos e tente novamente.");
    }
}
