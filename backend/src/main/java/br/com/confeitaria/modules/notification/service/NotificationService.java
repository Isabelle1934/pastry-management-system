package br.com.confeitaria.modules.notification.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.exception.NotFoundException;
import br.com.confeitaria.modules.notification.entity.Notification;
import br.com.confeitaria.modules.notification.entity.NotificationType;
import br.com.confeitaria.modules.notification.repository.NotificationRepository;

/**
 * Avisos para a proprietária. A criação é idempotente pela {@code dedupKey} (INSERT … ON CONFLICT
 * DO NOTHING): processos agendados podem rodar quantas vezes for preciso sem duplicar avisos.
 * O e-mail é enviado depois do commit; falhas são reenviadas pelo processo agendado.
 */
@Service
public class NotificationService {

    static final int MAX_EMAIL_ATTEMPTS = 5;

    /** Publicado quando um aviso com e-mail é criado; o envio acontece após o commit. */
    public record NotificationCreated(Long notificationId) {
    }

    private final NotificationRepository repository;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, JdbcTemplate jdbc, ApplicationEventPublisher events,
            Clock clock) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    /** @return o id do aviso criado, ou vazio se já existia um com a mesma chave */
    @Transactional
    public Optional<Long> notify(NotificationType type, Long orderId, String message, String dedupKey) {
        List<Long> ids = jdbc.queryForList("""
                insert into notification (type, order_id, message, dedup_key, email_status, created_at)
                values (?, ?, ?, ?, ?, ?)
                on conflict (dedup_key) do nothing
                returning id
                """, Long.class, type.name(), orderId, truncate(message), dedupKey,
                type.sendsEmail() ? Notification.EmailStatus.PENDING.name() : Notification.EmailStatus.SKIPPED.name(),
                Timestamp.from(clock.instant()));
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        if (type.sendsEmail()) {
            events.publishEvent(new NotificationCreated(ids.getFirst()));
        }
        return Optional.of(ids.getFirst());
    }

    @Transactional(readOnly = true)
    public List<Notification> recent(boolean onlyUnread, int limit) {
        PageRequest page = PageRequest.of(0, Math.clamp(limit, 1, 100));
        return onlyUnread ? repository.findByReadAtIsNullOrderByCreatedAtDescIdDesc(page)
                : repository.findAllByOrderByCreatedAtDescIdDesc(page);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByReadAtIsNull();
    }

    @Transactional
    public void markRead(Long id) {
        repository.findById(id).orElseThrow(() -> new NotFoundException("Aviso não encontrado.")).markRead(clock.instant());
    }

    @Transactional
    public int markAllRead() {
        return repository.markAllRead(clock.instant());
    }

    /** Avisos cujo e-mail ainda não saiu (falha ou envio pendente há mais de 1 minuto). */
    @Transactional(readOnly = true)
    public List<Long> emailsToRetry() {
        Instant before = clock.instant().minus(Duration.ofMinutes(1));
        return repository.findEmailsToRetry(MAX_EMAIL_ATTEMPTS, before, PageRequest.of(0, 50)).stream()
                .map(Notification::getId).toList();
    }

    private static String truncate(String message) {
        return message.length() <= 500 ? message : message.substring(0, 497) + "...";
    }
}
