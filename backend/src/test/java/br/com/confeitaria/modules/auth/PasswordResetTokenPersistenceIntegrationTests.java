package br.com.confeitaria.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.confeitaria.modules.auth.entity.AdminUser;
import br.com.confeitaria.modules.auth.entity.PasswordResetToken;
import br.com.confeitaria.modules.auth.repository.AdminUserRepository;
import br.com.confeitaria.modules.auth.repository.PasswordResetTokenRepository;
import br.com.confeitaria.support.IntegrationTest;

@IntegrationTest
class PasswordResetTokenPersistenceIntegrationTests {

    @Autowired
    private AdminUserRepository users;

    @Autowired
    private PasswordResetTokenRepository tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    private AdminUser newUser() {
        return users.saveAndFlush(new AdminUser("reset." + UUID.randomUUID() + "@exemplo.com", "Dona", "hash", Instant.now()));
    }

    private static String randomHash() {
        byte[] bytes = new byte[32];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @Test
    void storesAndFindsTokenByHash() {
        AdminUser user = newUser();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String hash = randomHash();
        tokens.saveAndFlush(new PasswordResetToken(user, hash, now, now.plus(Duration.ofMinutes(30))));

        PasswordResetToken found = tokens.findByTokenHash(hash).orElseThrow();
        assertThat(found.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
        assertThat(found.isUsable(now)).isTrue();
        assertThat(found.isUsable(now.plus(Duration.ofMinutes(31)))).isFalse();
    }

    @Test
    void invalidatesActiveTokensOfTheUserOnly() {
        AdminUser user = newUser();
        AdminUser other = newUser();
        Instant now = Instant.now();
        String first = randomHash();
        String second = randomHash();
        String othersToken = randomHash();

        tx.executeWithoutResult(status -> {
            tokens.save(new PasswordResetToken(user, first, now, now.plusSeconds(1800)));
            tokens.save(new PasswordResetToken(user, second, now, now.plusSeconds(1800)));
            tokens.save(new PasswordResetToken(other, othersToken, now, now.plusSeconds(1800)));
        });

        Integer invalidated = tx.execute(status -> tokens.invalidateActiveTokens(user.getId(), now));

        assertThat(invalidated).isEqualTo(2);
        assertThat(tokens.findByTokenHash(first).orElseThrow().isUsable(now)).isFalse();
        assertThat(tokens.findByTokenHash(second).orElseThrow().isUsable(now)).isFalse();
        assertThat(tokens.findByTokenHash(othersToken).orElseThrow().isUsable(now)).isTrue();
    }

    @Test
    void tokensAreRemovedWhenUserIsDeleted() {
        AdminUser user = newUser();
        String hash = randomHash();
        Instant now = Instant.now();
        tokens.saveAndFlush(new PasswordResetToken(user, hash, now, now.plusSeconds(60)));

        jdbc.update("delete from admin_user where id = ?", user.getId());

        assertThat(tokens.findByTokenHash(hash)).isEmpty();
    }
}
