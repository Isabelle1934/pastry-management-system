package br.com.confeitaria.modules.tracking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.exception.ApiException;
import br.com.confeitaria.common.exception.TooManyRequestsException;
import br.com.confeitaria.common.ratelimit.FixedWindowCounter;
import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.order.repository.CakeOrderRepository;
import br.com.confeitaria.modules.order.service.TrackingCodes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Acesso da cliente ao próprio pedido (ADR-007): código de acompanhamento + 4 últimos dígitos do
 * WhatsApp. Após a verificação, a sessão do navegador fica autorizada só para aquele pedido, por 2 horas.
 */
@Component
public class TrackingAccess {

    static final String SESSION_ORDER_ID = "tracking.orderId";
    static final String SESSION_VERIFIED_AT = "tracking.verifiedAt";
    static final Duration ACCESS_DURATION = Duration.ofHours(2);

    public static final String NOT_FOUND = "TRACKING_NOT_FOUND";
    public static final String REQUIRED = "TRACKING_REQUIRED";

    private final CakeOrderRepository orders;
    private final Clock clock;
    private final FixedWindowCounter attemptsByIp;
    private final FixedWindowCounter failuresByCode;

    public TrackingAccess(CakeOrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
        this.attemptsByIp = new FixedWindowCounter(20, Duration.ofMinutes(15), clock);
        this.failuresByCode = new FixedWindowCounter(5, Duration.ofMinutes(15), clock);
    }

    /** Mesma resposta para código inexistente e WhatsApp que não confere (não revela se o código existe). */
    @Transactional(readOnly = true)
    public void verify(String trackingCode, String whatsappLast4, HttpServletRequest request) {
        if (!attemptsByIp.tryAcquire(request.getRemoteAddr())) {
            throw new TooManyRequestsException();
        }
        Optional<String> code = TrackingCodes.normalize(trackingCode);
        if (code.isPresent() && failuresByCode.isLimitReached(code.get())) {
            throw new TooManyRequestsException();
        }
        Long orderId = code.flatMap(orders::findByTrackingCode)
                .filter(order -> order.getCustomerWhatsapp().endsWith(whatsappLast4))
                .map(CakeOrder::getId)
                .orElse(null);
        if (orderId == null) {
            code.ifPresent(failuresByCode::increment);
            throw new TrackingException(HttpStatus.UNAUTHORIZED, NOT_FOUND,
                    "Não encontramos um pedido com esse código e WhatsApp. Confira os dados e tente novamente.");
        }
        failuresByCode.reset(code.get());

        // Novo ID de sessão a cada verificação (proteção contra fixação de sessão).
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_ORDER_ID, orderId);
        session.setAttribute(SESSION_VERIFIED_AT, clock.instant());
    }

    /** Pedido autorizado nesta sessão; 401 se não houver verificação válida. */
    public Long currentOrderId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object orderId = session == null ? null : session.getAttribute(SESSION_ORDER_ID);
        Object verifiedAt = session == null ? null : session.getAttribute(SESSION_VERIFIED_AT);
        if (orderId instanceof Long id && verifiedAt instanceof Instant at
                && clock.instant().isBefore(at.plus(ACCESS_DURATION))) {
            return id;
        }
        if (session != null) {
            session.removeAttribute(SESSION_ORDER_ID);
            session.removeAttribute(SESSION_VERIFIED_AT);
        }
        throw new TrackingException(HttpStatus.UNAUTHORIZED, REQUIRED,
                "Para ver o pedido, informe novamente o código e os 4 últimos dígitos do WhatsApp.");
    }

    public void forget(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_ORDER_ID);
            session.removeAttribute(SESSION_VERIFIED_AT);
        }
    }

    @Scheduled(fixedDelayString = "PT10M")
    void evictExpired() {
        attemptsByIp.evictExpired();
        failuresByCode.evictExpired();
    }

    /** Usado em testes. */
    public void clearLimits() {
        attemptsByIp.clear();
        failuresByCode.clear();
    }

    static final class TrackingException extends ApiException {

        TrackingException(HttpStatus status, String code, String message) {
            super(status, code, message);
        }
    }
}
