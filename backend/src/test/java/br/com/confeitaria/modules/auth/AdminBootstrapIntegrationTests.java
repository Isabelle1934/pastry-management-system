package br.com.confeitaria.modules.auth;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.modules.auth.entity.AdminUser;
import br.com.confeitaria.modules.auth.repository.AdminUserRepository;
import br.com.confeitaria.modules.auth.service.AdminBootstrap;
import br.com.confeitaria.modules.auth.service.AuthRateLimiter;
import br.com.confeitaria.support.AuthTestSupport;
import br.com.confeitaria.support.IntegrationTest;
import jakarta.servlet.http.Cookie;

@IntegrationTest
class AdminBootstrapIntegrationTests {

    @Autowired
    private AdminBootstrap bootstrap;

    @Autowired
    private AdminUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTestSupport support;

    @Autowired
    private AuthRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter.clear();
    }

    private static AuthProperties.Bootstrap config(String email, String password) {
        return new AuthProperties.Bootstrap(email, "Maria", password);
    }

    @Test
    void createsTheFirstAccountWithAProvisionalPassword() {
        tx.executeWithoutResult(status -> {
            // Simula um banco recém-criado; tudo é desfeito ao final do teste.
            jdbc.update("delete from admin_user");

            boolean created = bootstrap.createIfNoneExists(config(" Maria@Exemplo.com ", "senha-provisoria-1"));

            assertThat(created).isTrue();
            AdminUser user = users.findByEmail("maria@exemplo.com").orElseThrow();
            assertThat(user.getName()).isEqualTo("Maria");
            assertThat(user.isMustChangePassword()).isTrue();
            assertThat(user.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("senha-provisoria-1");
            assertThat(passwordEncoder.matches("senha-provisoria-1", user.getPasswordHash())).isTrue();
            status.setRollbackOnly();
        });
    }

    @Test
    void neverTouchesExistingAccounts() {
        support.createUser();
        long before = users.count();

        boolean created = tx.execute(status -> bootstrap.createIfNoneExists(config("outra@exemplo.com", "senha-provisoria-1")));

        assertThat(created).isFalse();
        assertThat(users.count()).isEqualTo(before);
        assertThat(users.findByEmail("outra@exemplo.com")).isEmpty();
    }

    @Test
    void rejectsAWeakInitialPassword() {
        tx.executeWithoutResult(status -> {
            jdbc.update("delete from admin_user");
            assertThatThrownBy(() -> bootstrap.createIfNoneExists(config("maria@exemplo.com", "123")))
                    .isInstanceOf(FieldValidationException.class);
            status.setRollbackOnly();
        });
    }

    @Test
    void provisionalPasswordBlocksThePanelUntilChanged() throws Exception {
        AdminUser user = support.createUser(true);
        Cookie session = login(mockMvc, user.getEmail(), PASSWORD);

        mockMvc.perform(get("/api/admin/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        mockMvc.perform(get("/api/admin/test-probe").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mockMvc.perform(post("/api/admin/auth/password/change").with(csrf()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"senha-definitiva-ok\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/test-probe").cookie(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/auth/me").cookie(session))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }
}
