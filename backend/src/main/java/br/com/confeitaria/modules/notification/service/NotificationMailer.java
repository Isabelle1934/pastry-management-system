package br.com.confeitaria.modules.notification.service;

import java.time.Clock;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.confeitaria.modules.auth.AuthProperties;
import br.com.confeitaria.modules.auth.repository.AdminUserRepository;
import br.com.confeitaria.modules.notification.entity.Notification;
import br.com.confeitaria.modules.notification.repository.NotificationRepository;
import br.com.confeitaria.modules.settings.service.SettingsService;

/**
 * Envia por e-mail os avisos que exigem atenção. Destino: e-mail de avisos das Configurações
 * ou, se não houver, o e-mail de acesso da proprietária. O texto não inclui dados pessoais da cliente
 * além do primeiro nome — o detalhe fica no painel.
 */
@Component
public class NotificationMailer {

    private final NotificationRepository repository;
    private final EmailService emailService;
    private final SettingsService settings;
    private final AdminUserRepository admins;
    private final AuthProperties authProperties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public NotificationMailer(NotificationRepository repository, EmailService emailService, SettingsService settings,
            AdminUserRepository admins, AuthProperties authProperties, PlatformTransactionManager transactionManager,
            Clock clock) {
        this.repository = repository;
        this.emailService = emailService;
        this.settings = settings;
        this.admins = admins;
        this.authProperties = authProperties;
        // Transação própria e explícita: o método é chamado tanto pelo listener (mesma classe) quanto pelo
        // processo agendado, e o resultado do envio precisa ser gravado nos dois casos.
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener
    public void onCreated(NotificationService.NotificationCreated event) {
        send(event.notificationId());
    }

    /** Envia (ou reenvia) o e-mail de um aviso. Idempotente: avisos já enviados são ignorados. */
    public void send(Long notificationId) {
        tx.executeWithoutResult(status -> sendInTransaction(notificationId));
    }

    private void sendInTransaction(Long notificationId) {
        Notification notification = repository.findById(notificationId).orElse(null);
        if (notification == null || notification.getEmailStatus() == Notification.EmailStatus.SENT
                || notification.getEmailStatus() == Notification.EmailStatus.SKIPPED) {
            return;
        }
        String to = recipient();
        if (to == null) {
            notification.emailSkipped();
            return;
        }
        String link = authProperties.publicBaseUrl().replaceAll("/+$", "")
                + (notification.getOrderId() == null ? "/admin" : "/admin/pedidos/" + notification.getOrderId());
        String body = """
                %s

                Abra o painel para ver os detalhes:
                %s
                """.formatted(notification.getMessage(), link);
        if (emailService.send(to, notification.getType().title(), body)) {
            notification.emailSent(clock.instant());
        } else {
            notification.emailFailed();
        }
    }

    private String recipient() {
        String configured = settings.current().getOwnerNotificationEmail();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return admins.findAll().stream().filter(a -> a.isEnabled()).map(a -> a.getEmail()).findFirst().orElse(null);
    }
}
