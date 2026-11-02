package br.com.confeitaria.modules.auth.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import br.com.confeitaria.modules.auth.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Trava a linha do token: duas redefinições simultâneas com o mesmo link não passam juntas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t join fetch t.adminUser where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(String tokenHash);

    /** Invalida tokens ainda não usados da usuária (ao gerar um novo ou trocar a senha). */
    @Modifying
    @Query("""
            update PasswordResetToken t set t.usedAt = :now
            where t.adminUser.id = :adminUserId and t.usedAt is null
            """)
    int invalidateActiveTokens(Long adminUserId, Instant now);
}
