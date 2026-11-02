package br.com.confeitaria.modules.notification.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Aviso para a proprietária, exibido no painel e (conforme o tipo) enviado por e-mail. */
@Entity
@Table(name = "notification")
public class Notification {

    public enum EmailStatus {
        PENDING,
        SENT,
        FAILED,
        SKIPPED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private NotificationType type;

    @Column(name = "order_id", updatable = false)
    private Long orderId;

    @Column(nullable = false, length = 500, updatable = false)
    private String message;

    @Column(name = "dedup_key", nullable = false, length = 120, updatable = false)
    private String dedupKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false, length = 20)
    private EmailStatus emailStatus;

    @Column(name = "email_attempts", nullable = false)
    private int emailAttempts;

    @Column(name = "email_sent_at")
    private Instant emailSentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public void emailSent(Instant now) {
        emailStatus = EmailStatus.SENT;
        emailSentAt = now;
        emailAttempts++;
    }

    public void emailFailed() {
        emailStatus = EmailStatus.FAILED;
        emailAttempts++;
    }

    public void emailSkipped() {
        emailStatus = EmailStatus.SKIPPED;
    }

    public Long getId() {
        return id;
    }

    public NotificationType getType() {
        return type;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getMessage() {
        return message;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public EmailStatus getEmailStatus() {
        return emailStatus;
    }

    public int getEmailAttempts() {
        return emailAttempts;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
