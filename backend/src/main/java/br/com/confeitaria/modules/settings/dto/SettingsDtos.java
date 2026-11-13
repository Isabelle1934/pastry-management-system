package br.com.confeitaria.modules.settings.dto;

import java.math.BigDecimal;

import br.com.confeitaria.common.validation.WhatsAppNumber;
import br.com.confeitaria.modules.settings.entity.BakerySettings;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    /** Edição completa das configurações (painel). Campos de texto vazios são gravados como "não informado". */
    public record SettingsRequest(
            @Size(max = 120, message = "Máximo de 120 caracteres.") String name,
            @Size(max = 2000, message = "Máximo de 2000 caracteres.") String aboutText,
            @Size(max = 30, message = "Número inválido.") String whatsappContact,
            @Size(max = 60, message = "Máximo de 60 caracteres.") String instagram,
            @Size(max = 300, message = "Máximo de 300 caracteres.") String address,
            @Size(max = 1000, message = "Máximo de 1000 caracteres.") String pickupInstructions,
            @Size(max = 77, message = "Chave Pix muito longa.") String pixKey,
            @Size(max = 120, message = "Máximo de 120 caracteres.") String pixHolderName,
            @NotNull(message = "Informe a validade do orçamento.")
            @Min(value = 1, message = "Mínimo de 1 hora.") @Max(value = 720, message = "Máximo de 720 horas (30 dias).")
            Integer quoteValidityHours,
            @NotNull(message = "Informe a antecedência do aviso.")
            @Min(value = 1, message = "Mínimo de 1 hora.") @Max(value = 719, message = "Máximo de 719 horas.")
            Integer quoteExpiryWarningHours,
            @Min(value = 1, message = "Mínimo de 1 hora.") @Max(value = 720, message = "Máximo de 720 horas (30 dias).")
            Integer depositDeadlineHours,
            @Email(message = "E-mail inválido.") @Size(max = 254) String ownerNotificationEmail,
            // Atendimento: campos vazios (null) mantêm o valor atual.
            Boolean pickupEnabled,
            Boolean deliveryEnabled,
            @Size(max = 500, message = "Máximo de 500 caracteres.") String deliveryInfo,
            Boolean customCakeEnabled,
            Boolean customCakeDelivery,
            // Campos vazios (null) mantêm o valor atual; link vazio ("") remove o link.
            @DecimalMin(value = "0.00", message = "A taxa não pode ser negativa.")
            @DecimalMax(value = "9999.99", message = "Valor muito alto.")
            @Digits(integer = 4, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal deliveryFee,
            @Size(max = 500, message = "Máximo de 500 caracteres.") String cardPaymentLink,
            @Min(value = 0, message = "Use 0 para não limitar.") @Max(value = 720, message = "Máximo de 720 horas (30 dias).")
            Integer depositCutoffHours,
            // Taxas por modalidade, aviso de pontualidade e lembrete (vazios mantêm o valor atual)
            @DecimalMin(value = "0.00", message = "A taxa não pode ser negativa.")
            @DecimalMax(value = "9999.99", message = "Valor muito alto.")
            @Digits(integer = 4, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal deliveryFeeSweets,
            @DecimalMin(value = "0.00", message = "A taxa não pode ser negativa.")
            @DecimalMax(value = "9999.99", message = "Valor muito alto.")
            @Digits(integer = 4, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal partyVenueFee,
            Boolean partyVenueFeeConfirmed,
            @Size(max = 1000, message = "Máximo de 1000 caracteres.") String punctualityNotice,
            @Min(value = 1, message = "Mínimo de 1 hora.") @Max(value = 720, message = "Máximo de 720 horas (30 dias).")
            Integer depositReminderHours,
            @NotNull(message = "Versão obrigatória.") Long version) {
    }

    public record SettingsResponse(String name, String aboutText, String whatsappContact, String instagram,
            String address, String pickupInstructions, String pixKey, String pixHolderName, int quoteValidityHours,
            int quoteExpiryWarningHours, Integer depositDeadlineHours, String ownerNotificationEmail,
            boolean pickupEnabled, boolean deliveryEnabled, String deliveryInfo, boolean customCakeEnabled,
            boolean customCakeDelivery, BigDecimal deliveryFee, String cardPaymentLink, int depositCutoffHours,
            BigDecimal deliveryFeeSweets, BigDecimal partyVenueFee, boolean partyVenueFeeConfirmed,
            String punctualityNotice, int depositReminderHours, long version) {

        public static SettingsResponse from(BakerySettings s) {
            return new SettingsResponse(s.getName(), s.getAboutText(), WhatsAppNumber.format(s.getWhatsappContact()),
                    s.getInstagram(), s.getAddress(), s.getPickupInstructions(), s.getPixKey(), s.getPixHolderName(),
                    s.getQuoteValidityHours(), s.getQuoteExpiryWarningHours(), s.getDepositDeadlineHours(),
                    s.getOwnerNotificationEmail(), s.isPickupEnabled(), s.isDeliveryEnabled(), s.getDeliveryInfo(),
                    s.isCustomCakeEnabled(), s.isCustomCakeDelivery(), s.getDeliveryFee(), s.getCardPaymentLink(),
                    s.getDepositCutoffHours(), s.getDeliveryFeeSweets(), s.getPartyVenueFee(),
                    s.isPartyVenueFeeConfirmed(), s.getPunctualityNotice(), s.getDepositReminderHours(), s.getVersion());
        }
    }

    /**
     * Informações exibidas na área pública. Não inclui chave Pix (mostrada só no acompanhamento
     * do pedido com sinal pendente), regras de orçamento nem o e-mail de avisos.
     */
    public record PublicBakeryInfo(String name, String aboutText, String whatsappContact, String whatsappLink,
            String instagram, String address, String pickupInstructions, boolean pickupEnabled,
            boolean deliveryEnabled, String deliveryInfo, BigDecimal deliveryFee, boolean customCakeEnabled,
            boolean customCakeDelivery, BigDecimal deliveryFeeSweets, BigDecimal partyVenueFee,
            String punctualityNotice) {

        public static PublicBakeryInfo from(BakerySettings s) {
            String whatsapp = s.getWhatsappContact();
            return new PublicBakeryInfo(s.getName(), s.getAboutText(), WhatsAppNumber.format(whatsapp),
                    whatsapp == null ? null : "https://wa.me/" + whatsapp.substring(1),
                    s.getInstagram(), s.getAddress(), s.getPickupInstructions(), s.isPickupEnabled(),
                    s.isDeliveryEnabled(), s.isDeliveryEnabled() ? s.getDeliveryInfo() : null,
                    s.isDeliveryEnabled() ? s.getDeliveryFee() : null, s.isCustomCakeEnabled(),
                    s.isDeliveryEnabled() && s.isCustomCakeDelivery(),
                    s.isDeliveryEnabled() ? s.getDeliveryFeeSweets() : null,
                    s.isDeliveryEnabled() && s.isPartyVenueFeeConfirmed() ? s.getPartyVenueFee() : null,
                    s.getPunctualityNotice());
        }
    }
}
