package br.com.confeitaria.common.exception;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import br.com.confeitaria.common.web.RequestIdFilter;
import jakarta.validation.ConstraintViolationException;

/**
 * Converte todas as exceções em {@code application/problem+json} (RFC 9457) com:
 * {@code code} estável, mensagem em português e {@code requestId} para rastreio no log.
 * Nunca expõe stack trace, SQL, nomes de classes ou dados enviados pelo usuário.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record FieldViolation(String field, String message) {
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldViolation> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                "Este registro foi alterado em outra tela. Recarregue a página e tente novamente.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        // Detalhes da constraint só no log: podem conter valores informados pelo usuário.
        log.warn("Violação de integridade de dados: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                "A operação conflita com dados já existentes.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Erro inesperado", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "Ocorreu um erro inesperado. Tente novamente em instantes.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        return asObject(validationProblem(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return asObject(validationProblem(errors));
    }

    /**
     * Demais exceções padrão do Spring MVC (JSON malformado, método não suportado, rota inexistente…):
     * mantém o status escolhido pelo Spring, mas troca a mensagem por uma em português e sem detalhes.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Erro do framework", ex);
        }
        Map.Entry<String, String> mapped = mapFrameworkStatus(statusCode);
        ProblemDetail detail = buildProblem(statusCode, mapped.getKey(), mapped.getValue());
        return ResponseEntity.status(statusCode).headers(headers).body(detail);
    }

    private static Map.Entry<String, String> mapFrameworkStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> Map.entry(ErrorCode.MALFORMED_REQUEST, "A requisição está incompleta ou em formato inválido.");
            case 404 -> Map.entry(ErrorCode.NOT_FOUND, "Recurso não encontrado.");
            case 405 -> Map.entry(ErrorCode.METHOD_NOT_ALLOWED, "Operação não permitida neste endereço.");
            case 413 -> Map.entry(ErrorCode.PAYLOAD_TOO_LARGE, "O conteúdo enviado excede o tamanho máximo permitido.");
            case 415 -> Map.entry(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Formato de conteúdo não suportado.");
            default -> status.is4xxClientError()
                    ? Map.entry(ErrorCode.MALFORMED_REQUEST, "Não foi possível processar a requisição.")
                    : Map.entry(ErrorCode.INTERNAL_ERROR, "Ocorreu um erro inesperado. Tente novamente em instantes.");
        };
    }

    private static FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }

    private static String lastNode(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot >= 0 ? propertyPath.substring(dot + 1) : propertyPath;
    }

    private static ResponseEntity<ProblemDetail> validationProblem(List<FieldViolation> errors) {
        ProblemDetail detail = buildProblem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Alguns campos não estão corretos. Verifique e tente novamente.");
        detail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(detail);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatusCode status, String code, String message) {
        return ResponseEntity.status(status).body(buildProblem(status, code, message));
    }

    private static ProblemDetail buildProblem(HttpStatusCode status, String code, String message) {
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResponseEntity<Object> asObject(ResponseEntity<ProblemDetail> response) {
        return (ResponseEntity) response;
    }
}
