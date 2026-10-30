package br.com.confeitaria.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.confeitaria.modules.auth.entity.AdminUser;
import br.com.confeitaria.modules.auth.repository.AdminUserRepository;
import br.com.confeitaria.support.IntegrationTest;

@IntegrationTest
class AdminUserPersistenceIntegrationTests {

    private static final String HASH = "{bcrypt}$2a$12$abcdefghijklmnopqrstuuJ8Kc0cC3m7o1yZ8lV0bq1tQ9yJ3s5aW";

    @Autowired
    private AdminUserRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    private static String uniqueEmail() {
        return "Dona." + UUID.randomUUID() + "@Exemplo.com.br";
    }

    @Test
    void persistsWithNormalizedEmailAndTimestamps() {
        String email = uniqueEmail();
        AdminUser saved = repository.saveAndFlush(new AdminUser("  " + email + " ", "  Proprietária ", HASH, Instant.now()));

        AdminUser found = repository.findByEmail(AdminUser.normalizeEmail(email)).orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getEmail()).isEqualTo(email.toLowerCase());
        assertThat(found.getName()).isEqualTo("Proprietária");
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.isMustChangePassword()).isFalse();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void rejectsDuplicateEmail() {
        String email = uniqueEmail();
        repository.saveAndFlush(new AdminUser(email, "Primeira", HASH, Instant.now()));

        assertThatThrownBy(() -> repository.saveAndFlush(new AdminUser(email.toUpperCase(), "Segunda", HASH, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsNonLowercaseEmailEvenOutsideTheApplication() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into admin_user (email, name, password_hash, password_changed_at, created_at, updated_at)
                values ('Maiuscula@Exemplo.com', 'X', 'h', now(), now(), now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentEditsAreDetectedByOptimisticLocking() {
        Long id = repository.saveAndFlush(new AdminUser(uniqueEmail(), "Original", HASH, Instant.now())).getId();

        AdminUser firstCopy = repository.findById(id).orElseThrow();
        AdminUser secondCopy = repository.findById(id).orElseThrow();

        firstCopy.requirePasswordChange();
        tx.executeWithoutResult(status -> repository.save(firstCopy));

        secondCopy.disable();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> repository.save(secondCopy)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        AdminUser current = repository.findById(id).orElseThrow();
        assertThat(current.isMustChangePassword()).isTrue();
        assertThat(current.isEnabled()).isTrue();
    }
}
