package br.com.confeitaria.modules.settings.service;

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
                .trackWithoutValues("ownerNotificationEmail", settings.getOwnerNotificationEmail(),
                        blankToNull(request.ownerNotificationEmail()));

        settings.updateProfile(blankToNull(request.name()), blankToNull(request.aboutText()), whatsapp, instagram,
                blankToNull(request.address()), blankToNull(request.pickupInstructions()));
        settings.updatePayment(blankToNull(request.pixKey()), blankToNull(request.pixHolderName()));
        settings.updateQuotes(request.quoteValidityHours(), request.quoteExpiryWarningHours());
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
