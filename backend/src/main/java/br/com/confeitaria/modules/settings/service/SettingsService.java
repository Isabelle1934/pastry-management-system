package br.com.confeitaria.modules.settings.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.audit.AuditService;
import br.com.confeitaria.common.audit.Changes;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.common.persistence.Versions;
import br.com.confeitaria.common.validation.WhatsAppNumber;
import br.com.confeitaria.modules.settings.dto.SettingsDtos.PublicBakeryInfo;
import br.com.confeitaria.modules.settings.dto.SettingsDtos.SettingsRequest;
import br.com.confeitaria.modules.settings.dto.SettingsDtos.SettingsResponse;
import br.com.confeitaria.modules.settings.entity.BakerySettings;
import br.com.confeitaria.modules.settings.repository.BakerySettingsRepository;

@Service
public class SettingsService {

    private final BakerySettingsRepository repository;
    private final AuditService audit;

    public SettingsService(BakerySettingsRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    /** Usado pelos demais módulos (orçamentos, avisos). */
    @Transactional(readOnly = true)
    public BakerySettings current() {
        return repository.findById(BakerySettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Linha de configurações ausente (migration V2)."));
    }

    /** Grava alterações feitas na entidade por outros módulos (ex.: regras da agenda). */
    public void flush() {
        repository.flush();
    }

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        return SettingsResponse.from(current());
    }

    @Transactional(readOnly = true)
    public PublicBakeryInfo publicInfo() {
        return PublicBakeryInfo.from(current());
    }

    @Transactional
    public SettingsResponse update(SettingsRequest request, Long actorId) {
        BakerySettings settings = current();
        Versions.require(settings.getVersion(), request.version());

        String whatsapp = normalizeWhatsApp(request.whatsappContact());
        String instagram = normalizeInstagram(request.instagram());
        if (request.quoteExpiryWarningHours() >= request.quoteValidityHours()) {
            throw new FieldValidationException("quoteExpiryWarningHours",
                    "O aviso precisa acontecer antes do vencimento (menos horas que a validade).");
        }

        boolean pickup = request.pickupEnabled() == null ? settings.isPickupEnabled() : request.pickupEnabled();
        boolean delivery = request.deliveryEnabled() == null ? settings.isDeliveryEnabled() : request.deliveryEnabled();
        if (!pickup && !delivery) {
            throw new FieldValidationException("pickupEnabled",
                    "Mantenha pelo menos uma forma de atendimento: retirada ou delivery.");
        }
        String deliveryInfo = request.deliveryInfo() == null ? settings.getDeliveryInfo()
                : blankToNull(request.deliveryInfo());
        boolean customCake = request.customCakeEnabled() == null ? settings.isCustomCakeEnabled()
                : request.customCakeEnabled();
        boolean customCakeDelivery = request.customCakeDelivery() == null ? settings.isCustomCakeDelivery()
                : request.customCakeDelivery();

        BigDecimal deliveryFee = request.deliveryFee() == null ? settings.getDeliveryFee()
                : request.deliveryFee().setScale(2);
        String cardLink = request.cardPaymentLink() == null ? settings.getCardPaymentLink()
                : normalizeCardLink(request.cardPaymentLink());
        int cutoff = request.depositCutoffHours() == null ? settings.getDepositCutoffHours()
                : request.depositCutoffHours();

        BigDecimal feeSweets = request.deliveryFeeSweets() == null ? settings.getDeliveryFeeSweets()
                : request.deliveryFeeSweets().setScale(2);
        BigDecimal partyFee = request.partyVenueFee() == null ? settings.getPartyVenueFee()
                : request.partyVenueFee().setScale(2);
        boolean partyConfirmed = request.partyVenueFeeConfirmed() == null ? settings.isPartyVenueFeeConfirmed()
                : request.partyVenueFeeConfirmed();
        String punctuality = request.punctualityNotice() == null ? settings.getPunctualityNotice()
                : blankToNull(request.punctualityNotice());
        if (punctuality == null) {
            throw new FieldValidationException("punctualityNotice", "Escreva o aviso de pontualidade.");
        }
        int reminder = request.depositReminderHours() == null ? settings.getDepositReminderHours()
                : request.depositReminderHours();

        Changes changes = new Changes()
                .track("name", settings.getName(), blankToNull(request.name()))
                .track("aboutText", settings.getAboutText(), blankToNull(request.aboutText()))
                .track("whatsappContact", settings.getWhatsappContact(), whatsapp)
                .track("instagram", settings.getInstagram(), instagram)
                .track("address", settings.getAddress(), blankToNull(request.address()))
                .track("pickupInstructions", settings.getPickupInstructions(), blankToNull(request.pickupInstructions()))
                .trackWithoutValues("pixKey", settings.getPixKey(), blankToNull(request.pixKey()))
                .trackWithoutValues("pixHolderName", settings.getPixHolderName(), blankToNull(request.pixHolderName()))
                .track("quoteValidityHours", settings.getQuoteValidityHours(), request.quoteValidityHours())
                .track("quoteExpiryWarningHours", settings.getQuoteExpiryWarningHours(), request.quoteExpiryWarningHours())
                .track("depositDeadlineHours", settings.getDepositDeadlineHours(), request.depositDeadlineHours())
                .trackWithoutValues("ownerNotificationEmail", settings.getOwnerNotificationEmail(),
                        blankToNull(request.ownerNotificationEmail()))
                .track("pickupEnabled", settings.isPickupEnabled(), pickup)
                .track("deliveryEnabled", settings.isDeliveryEnabled(), delivery)
                .track("deliveryInfo", settings.getDeliveryInfo(), deliveryInfo)
                .track("customCakeEnabled", settings.isCustomCakeEnabled(), customCake)
                .track("customCakeDelivery", settings.isCustomCakeDelivery(), customCakeDelivery)
                .track("deliveryFee", settings.getDeliveryFee(), deliveryFee)
                .trackWithoutValues("cardPaymentLink", settings.getCardPaymentLink(), cardLink)
                .track("depositCutoffHours", settings.getDepositCutoffHours(), cutoff)
                .track("deliveryFeeSweets", settings.getDeliveryFeeSweets(), feeSweets)
                .track("partyVenueFee", settings.getPartyVenueFee(), partyFee)
                .track("partyVenueFeeConfirmed", settings.isPartyVenueFeeConfirmed(), partyConfirmed)
                .track("punctualityNotice", settings.getPunctualityNotice(), punctuality)
                .track("depositReminderHours", settings.getDepositReminderHours(), reminder);

        settings.updateProfile(blankToNull(request.name()), blankToNull(request.aboutText()), whatsapp, instagram,
                blankToNull(request.address()), blankToNull(request.pickupInstructions()));
        settings.updatePayment(blankToNull(request.pixKey()), blankToNull(request.pixHolderName()), cardLink);
        settings.updateDepositCutoff(cutoff);
        settings.updateDeliveryFees(feeSweets, partyFee, partyConfirmed);
        settings.updateCustomerMessages(punctuality, reminder);
        settings.updateQuotes(request.quoteValidityHours(), request.quoteExpiryWarningHours(),
                request.depositDeadlineHours());
        settings.updateFulfillment(pickup, delivery, deliveryInfo, deliveryFee, customCake, customCakeDelivery);
        settings.updateNotifications(blankToNull(request.ownerNotificationEmail()) == null
                ? null : request.ownerNotificationEmail().trim().toLowerCase());
        repository.flush();

        if (!changes.isEmpty()) {
            audit.record(ActorType.OWNER, actorId, "SETTINGS_UPDATED", "bakery_settings", BakerySettings.SINGLETON_ID,
                    changes.asMap());
        }
        return SettingsResponse.from(settings);
    }

    private static String normalizeWhatsApp(String value) {
        if (blankToNull(value) == null) {
            return null;
        }
        return WhatsAppNumber.normalize(value).orElseThrow(() -> new FieldValidationException("whatsappContact",
                "Informe um número de WhatsApp válido com DDD, ex.: (11) 99999-8888."));
    }

    /** Aceita "@confeitaria", "confeitaria" ou o link do perfil; guarda só o nome de usuário. */
    private static String normalizeInstagram(String value) {
        String handle = blankToNull(value);
        if (handle == null) {
            return null;
        }
        handle = handle.replaceFirst("(?i)^https?://(www\\.)?instagram\\.com/", "").replaceAll("[/?].*$", "")
                .replaceFirst("^@", "");
        if (!handle.matches("[A-Za-z0-9._]{1,30}")) {
            throw new FieldValidationException("instagram", "Informe o usuário do Instagram, ex.: @minhaconfeitaria.");
        }
        return handle;
    }

    /** Só links https completos: o link é mostrado para a cliente abrir no navegador. */
    private static String normalizeCardLink(String value) {
        String link = blankToNull(value);
        if (link == null) {
            return null;
        }
        try {
            URI uri = new URI(link);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null) {
                return link;
            }
        } catch (URISyntaxException e) {
            // segue para a mensagem de erro
        }
        throw new FieldValidationException("cardPaymentLink", "Informe o link completo, começando com https://.");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
