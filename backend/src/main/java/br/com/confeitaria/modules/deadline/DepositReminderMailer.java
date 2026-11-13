package br.com.confeitaria.modules.deadline;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import br.com.confeitaria.common.text.Texts;
import br.com.confeitaria.config.AppProperties;
import br.com.confeitaria.modules.auth.AuthProperties;
import br.com.confeitaria.modules.notification.service.EmailService;
import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.order.service.TrackingCodes;
import br.com.confeitaria.modules.quote.entity.Quote;

/**
 * Lembrete de pagamento do sinal enviado à cliente que informou e-mail no pedido. Não confirma nada:
 * só lembra o valor, o prazo e onde enviar o comprovante.
 */
@Component
public class DepositReminderMailer {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final EmailService email;
    private final AuthProperties auth;
    private final AppProperties properties;

    public DepositReminderMailer(EmailService email, AuthProperties auth, AppProperties properties) {
        this.email = email;
        this.auth = auth;
        this.properties = properties;
    }

    /** @return {@code true} se o e-mail foi aceito pelo servidor; {@code false} sem e-mail ou com falha */
    public boolean send(CakeOrder order, Quote quote, Instant dueAt, String bakeryName) {
        if (order.getCustomerEmail() == null) {
            return false;
        }
        ZoneId zone = properties.timezone();
        String shop = bakeryName == null || bakeryName.isBlank() ? "a confeitaria" : bakeryName;
        String link = auth.publicBaseUrl().replaceAll("/+$", "") + "/acompanhar";
        String body = """
                Olá, %s!

                Este é um lembrete de %s: o sinal de %s para confirmar sua encomenda de %s às %s ainda não foi identificado.

                Prazo para o pagamento: %s.
                Depois desse prazo, a solicitação pode ser cancelada automaticamente.

                Para pagar e enviar o comprovante, acesse %s com o código %s e os 4 últimos dígitos do seu WhatsApp.

                Se você já pagou, envie o comprovante pelo acompanhamento — a confeitaria confere e confirma.
                """.formatted(order.getCustomerName().split(" ")[0], shop, Texts.brl(quote.getDepositAmount()),
                Texts.date(order.getPickupDate()), order.getPickupTime(), WHEN.format(dueAt.atZone(zone)), link,
                TrackingCodes.format(order.getTrackingCode()));
        return email.send(order.getCustomerEmail(), "Lembrete: pagamento do sinal da sua encomenda", body);
    }
}
