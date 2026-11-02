package br.com.confeitaria.modules.order.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.confeitaria.common.exception.TooManyRequestsException;
import br.com.confeitaria.common.ratelimit.FixedWindowCounter;

/** Limita o envio de solicitações por IP (proteção contra spam e abuso de upload). */
@Component
public class OrderRateLimiter {

    static final int ORDERS_PER_IP_PER_HOUR = 5;

    private final FixedWindowCounter ordersByIp;

    public OrderRateLimiter(Clock clock) {
        this.ordersByIp = new FixedWindowCounter(ORDERS_PER_IP_PER_HOUR, Duration.ofHours(1), clock);
    }

    public void acquire(String ip) {
        if (!ordersByIp.tryAcquire(ip)) {
            throw new TooManyRequestsException();
        }
    }

    @Scheduled(fixedDelayString = "PT10M")
    void evictExpired() {
        ordersByIp.evictExpired();
    }

    /** Usado em testes para isolar cenários. */
    public void clear() {
        ordersByIp.clear();
    }
}
