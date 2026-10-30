package br.com.confeitaria.modules.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.confeitaria.modules.auth.entity.AdminUser;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    /** O e-mail deve ser passado já normalizado ({@link AdminUser#normalizeEmail(String)}). */
    Optional<AdminUser> findByEmail(String email);
}
