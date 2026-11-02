package br.com.confeitaria.modules.settings.entity;

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

    public void updatePayment(String pixKey, String pixHolderName) {
        this.pixKey = pixKey;
        this.pixHolderName = pixHolderName;
    }

    public void updateQuotes(int quoteValidityHours, int quoteExpiryWarningHours) {
        this.quoteValidityHours = quoteValidityHours;
        this.quoteExpiryWarningHours = quoteExpiryWarningHours;
    }

    public void updateNotifications(String ownerNotificationEmail) {
        this.ownerNotificationEmail = ownerNotificationEmail;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
