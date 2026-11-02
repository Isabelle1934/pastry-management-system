package br.com.confeitaria.modules.auth.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import br.com.confeitaria.modules.auth.AuthProperties;
import br.com.confeitaria.modules.notification.service.EmailService;

/**
 * Envia o link de redefinição após o commit e fora da thread da requisição: o tempo de resposta
 * não muda conforme o e-mail exista ou não, e uma falha de SMTP não desfaz a solicitação.
 * O token vai no fragmento ({@code #token=}), que o navegador não envia ao servidor nem a logs de acesso.
 */
@Component
class PasswordResetMailer {

    private final EmailService emailService;
    private final AuthProperties properties;

    PasswordResetMailer(EmailService emailService, AuthProperties properties) {
        this.emailService = emailService;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener
    void onResetRequested(PasswordResetRequestedEvent event) {
        String link = properties.publicBaseUrl().replaceAll("/+$", "") + "/admin/redefinir-senha#token=" + event.rawToken();
        long minutes = event.validity().toMinutes();
        String body = """
                Olá, %s.

                Recebemos um pedido para redefinir a senha do painel administrativo.
                Para criar uma nova senha, acesse o link abaixo (válido por %d minutos):

                %s

                Se você não fez este pedido, ignore este e-mail. Sua senha continua a mesma.
                """.formatted(event.name(), minutes, link);
        emailService.send(event.email(), "Redefinição de senha", body);
    }
}
