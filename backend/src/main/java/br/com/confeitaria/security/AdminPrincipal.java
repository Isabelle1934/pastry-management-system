package br.com.confeitaria.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import br.com.confeitaria.modules.auth.entity.AdminUser;

/**
 * Usuária autenticada guardada na sessão (serializada pelo Spring Session JDBC).
 * O hash da senha é apagado após a autenticação ({@link #eraseCredentials()}).
 */
public final class AdminPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String ROLE_OWNER = "ROLE_OWNER";

    private final Long id;
    private final String email;
    private final String name;
    private final boolean enabled;
    private String passwordHash;

    private AdminPrincipal(Long id, String email, String name, boolean enabled, String passwordHash) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.enabled = enabled;
        this.passwordHash = passwordHash;
    }

    public static AdminPrincipal from(AdminUser user) {
        return new AdminPrincipal(user.getId(), user.getEmail(), user.getName(), user.isEnabled(), user.getPasswordHash());
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROLE_OWNER));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** O e-mail é o identificador de login e o índice das sessões no Spring Session. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
