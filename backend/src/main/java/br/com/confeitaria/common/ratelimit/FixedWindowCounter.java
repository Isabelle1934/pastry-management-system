package br.com.confeitaria.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Contador por chave em janela fixa, em memória (adequado para instância única — ver ADR-018).
 * Operações atômicas por chave via {@link ConcurrentHashMap#compute}.
 */
public class FixedWindowCounter {

    private record Window(Instant start, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final Duration window;
    private final Clock clock;

    public FixedWindowCounter(int limit, Duration window, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit deve ser >= 1");
        }
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    /** Já atingiu o limite na janela atual? Não altera o contador. */
    public boolean isLimitReached(String key) {
        Window current = windows.get(key);
        return current != null && !isExpired(current, clock.instant()) && current.count() >= limit;
    }

    /** Registra uma ocorrência (ex.: tentativa de login malsucedida). */
    public void increment(String key) {
        Instant now = clock.instant();
        windows.compute(key, (k, w) -> w == null || isExpired(w, now) ? new Window(now, 1) : new Window(w.start(), w.count() + 1));
    }

    /** Consome uma unidade se ainda houver; retorna {@code false} quando o limite já foi atingido. */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        AtomicBoolean acquired = new AtomicBoolean(false);
        windows.compute(key, (k, w) -> {
            if (w == null || isExpired(w, now)) {
                acquired.set(true);
                return new Window(now, 1);
            }
            if (w.count() >= limit) {
                return w;
            }
            acquired.set(true);
            return new Window(w.start(), w.count() + 1);
        });
        return acquired.get();
    }

    public void reset(String key) {
        windows.remove(key);
    }

    public void evictExpired() {
        Instant now = clock.instant();
        windows.values().removeIf(w -> isExpired(w, now));
    }

    public void clear() {
        windows.clear();
    }

    private boolean isExpired(Window w, Instant now) {
        return !now.isBefore(w.start().plus(window));
    }
}
