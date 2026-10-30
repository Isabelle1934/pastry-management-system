package br.com.confeitaria.common.exception;

import org.springframework.http.HttpStatus;

/** Violação de regra de negócio (422). O código identifica a regra, ex.: {@code SLOT_FULL}. */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }

    public BusinessRuleException(String message) {
        this(ErrorCode.BUSINESS_RULE, message);
    }
}
