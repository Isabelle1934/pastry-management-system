package br.com.confeitaria.modules.auth.entity;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import br.com.confeitaria.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_user")
public class AdminUser extends BaseEntity {

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    protected AdminUser() {
    }

    public AdminUser(String email, String name, String passwordHash, Instant now) {
        this.email = normalizeEmail(email);
        this.name = Objects.requireNonNull(name).trim();
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.passwordChangedAt = Objects.requireNonNull(now);
    }

    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email).trim().toLowerCase(Locale.ROOT);
    }

    public void changePassword(String newPasswordHash, Instant now) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
        this.passwordChangedAt = Objects.requireNonNull(now);
        this.mustChangePassword = false;
    }

    public void requirePasswordChange() {
        this.mustChangePassword = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled = true;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }
}
