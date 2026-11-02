package br.com.confeitaria.common.exception;

import java.net.URI;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import br.com.confeitaria.common.web.RequestIdFilter;

/** Monta o corpo de erro padrão da API (usado pelo handler MVC e pelos filtros de segurança). */
public final class ProblemDetailFactory {

    private ProblemDetailFactory() {
    }

    public static ProblemDetail create(HttpStatusCode status, String code, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setType(URI.create("about:blank"));
        HttpStatus resolved = HttpStatus.resolve(status.value());
        detail.setTitle(resolved != null ? resolved.getReasonPhrase() : "Error");
        detail.setProperty("code", code);
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            detail.setProperty("requestId", requestId);
        }
        return detail;
    }
}
