package br.com.confeitaria.modules.settings.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Configurações da confeitaria: linha única (id = 1), criada pela migration V2. */
@Entity
@Table(name = "bakery_settings")
public class BakerySettings {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(length = 120)
    private String name;

    @Column(name = "about_text", length = 2000)
    private String aboutText;

    @Column(name = "whatsapp_contact", length = 20)
    private String whatsappContact;

    @Column(length = 60)
    private String instagram;

    @Column(length = 300)
    private String address;

    @Column(name = "pickup_instructions", length = 1000)
    private String pickupInstructions;

    @Column(name = "pix_key", length = 77)
    private String pixKey;

    @Column(name = "pix_holder_name", length = 120)
    private String pixHolderName;

    @Column(name = "quote_validity_hours", nullable = false)
    private int quoteValidityHours;

    @Column(name = "quote_expiry_warning_hours", nullable = false)
    private int quoteExpiryWarningHours;

    @Column(name = "owner_notification_email", length = 254)
    private String ownerNotificationEmail;

    @Column(name = "min_lead_time_hours", nullable = false)
    private int minLeadTimeHours;

    @Column(name = "max_booking_days_ahead", nullable = false)
    private int maxBookingDaysAhead;

    /** Horas após o aceite para pagar o sinal; {@code null} = sem prazo. */
    @Column(name = "deposit_deadline_hours")
    private Integer depositDeadlineHours;

    @Column(name = "pickup_enabled", nullable = false)
    private boolean pickupEnabled = true;

    @Column(name = "delivery_enabled", nullable = false)
    private boolean deliveryEnabled;

    /** Área atendida, taxa e prazos do delivery, exibidos para a cliente. */
    @Column(name = "delivery_info", length = 500)
    private String deliveryInfo;

    @Column(name = "custom_cake_enabled", nullable = false)
    private boolean customCakeEnabled = true;

    /** Bolos personalizados podem ser entregues por delivery. */
    @Column(name = "custom_cake_delivery", nullable = false)
    private boolean customCakeDelivery;

    /** Taxa de entrega dentro da cidade, sugerida no orçamento de pedidos com delivery. */
    @Column(name = "delivery_fee", nullable = false, precision = 8, scale = 2)
    private BigDecimal deliveryFee = new BigDecimal("20.00");

    /** Link de pagamento no cartão de crédito (gerado pela proprietária na maquininha/banco). */
    @Column(name = "card_payment_link", length = 500)
    private String cardPaymentLink;

    /** O sinal precisa ser pago até estas horas antes da data agendada; 0 = sem limite. */
    @Column(name = "deposit_cutoff_hours", nullable = false)
    private int depositCutoffHours = 24;

    /** Delivery de outros doces (sem bolo personalizado) dentro da cidade. */
    @Column(name = "delivery_fee_sweets", nullable = false, precision = 8, scale = 2)
    private BigDecimal deliveryFeeSweets = new BigDecimal("5.00");

    /** Entrega no local da festa. Só entra sozinha no orçamento depois de confirmada pela proprietária. */
    @Column(name = "party_venue_fee", nullable = false, precision = 8, scale = 2)
    private BigDecimal partyVenueFee = new BigDecimal("30.00");

    @Column(name = "party_venue_fee_confirmed", nullable = false)
    private boolean partyVenueFeeConfirmed;

    /** Aviso de pontualidade mostrado antes de enviar a encomenda e no acompanhamento. */
    @Column(name = "punctuality_notice", nullable = false, length = 1000)
    private String punctualityNotice = "Pedimos que a retirada ou a entrega aconteça no horário combinado.";

    /** Lembrete de pagamento do sinal: horas antes do fim do prazo. */
    @Column(name = "deposit_reminder_hours", nullable = false)
    private int depositReminderHours = 12;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected BakerySettings() {
    }

    public void updateProfile(String name, String aboutText, String whatsappContact, String instagram, String address,
            String pickupInstructions) {
        this.name = name;
        this.aboutText = aboutText;
        this.whatsappContact = whatsappContact;
        this.instagram = instagram;
        this.address = address;
        this.pickupInstructions = pickupInstructions;
    }

    public void updatePayment(String pixKey, String pixHolderName, String cardPaymentLink) {
        this.pixKey = pixKey;
        this.pixHolderName = pixHolderName;
        this.cardPaymentLink = cardPaymentLink;
    }

    public void updateDepositCutoff(int depositCutoffHours) {
        this.depositCutoffHours = depositCutoffHours;
    }

    public void updateDeliveryFees(BigDecimal deliveryFeeSweets, BigDecimal partyVenueFee, boolean partyVenueFeeConfirmed) {
        this.deliveryFeeSweets = deliveryFeeSweets;
        this.partyVenueFee = partyVenueFee;
        this.partyVenueFeeConfirmed = partyVenueFeeConfirmed;
    }

    public void updateCustomerMessages(String punctualityNotice, int depositReminderHours) {
        this.punctualityNotice = punctualityNotice;
        this.depositReminderHours = depositReminderHours;
    }

    public BigDecimal getDeliveryFeeSweets() {
        return deliveryFeeSweets;
    }

    public BigDecimal getPartyVenueFee() {
        return partyVenueFee;
    }

    public boolean isPartyVenueFeeConfirmed() {
        return partyVenueFeeConfirmed;
    }

    public String getPunctualityNotice() {
        return punctualityNotice;
    }

    public int getDepositReminderHours() {
        return depositReminderHours;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public String getCardPaymentLink() {
        return cardPaymentLink;
    }

    public int getDepositCutoffHours() {
        return depositCutoffHours;
    }

    public void updateQuotes(int quoteValidityHours, int quoteExpiryWarningHours, Integer depositDeadlineHours) {
        this.quoteValidityHours = quoteValidityHours;
        this.quoteExpiryWarningHours = quoteExpiryWarningHours;
        this.depositDeadlineHours = depositDeadlineHours;
    }

    public void updateNotifications(String ownerNotificationEmail) {
        this.ownerNotificationEmail = ownerNotificationEmail;
    }

    public void updateFulfillment(boolean pickupEnabled, boolean deliveryEnabled, String deliveryInfo,
            BigDecimal deliveryFee, boolean customCakeEnabled, boolean customCakeDelivery) {
        this.deliveryFee = deliveryFee;
        this.pickupEnabled = pickupEnabled;
        this.deliveryEnabled = deliveryEnabled;
        this.deliveryInfo = deliveryInfo;
        this.customCakeEnabled = customCakeEnabled;
        this.customCakeDelivery = customCakeDelivery;
    }

    public boolean isPickupEnabled() {
        return pickupEnabled;
    }

    public boolean isDeliveryEnabled() {
        return deliveryEnabled;
    }

    public String getDeliveryInfo() {
        return deliveryInfo;
    }

    public boolean isCustomCakeEnabled() {
        return customCakeEnabled;
    }

    public boolean isCustomCakeDelivery() {
        return customCakeDelivery;
    }

    public void updateScheduling(int minLeadTimeHours, int maxBookingDaysAhead) {
        this.minLeadTimeHours = minLeadTimeHours;
        this.maxBookingDaysAhead = maxBookingDaysAhead;
    }

    public String getName() {
        return name;
    }

    public String getAboutText() {
        return aboutText;
    }

    public String getWhatsappContact() {
        return whatsappContact;
    }

    public String getInstagram() {
        return instagram;
    }

    public String getAddress() {
        return address;
    }

    public String getPickupInstructions() {
        return pickupInstructions;
    }

    public String getPixKey() {
        return pixKey;
    }

    public String getPixHolderName() {
        return pixHolderName;
    }

    public int getQuoteValidityHours() {
        return quoteValidityHours;
    }

    public int getQuoteExpiryWarningHours() {
        return quoteExpiryWarningHours;
    }

    public String getOwnerNotificationEmail() {
        return ownerNotificationEmail;
    }

    public Integer getDepositDeadlineHours() {
        return depositDeadlineHours;
    }

    public int getMinLeadTimeHours() {
        return minLeadTimeHours;
    }

    public int getMaxBookingDaysAhead() {
        return maxBookingDaysAhead;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
