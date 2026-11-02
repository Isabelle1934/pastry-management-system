package br.com.confeitaria.modules.auth.service;

import java.time.Clock;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.audit.AuditService;
import br.com.confeitaria.modules.auth.AuthProperties;
import br.com.confeitaria.modules.auth.entity.AdminUser;
import br.com.confeitaria.modules.auth.repository.AdminUserRepository;

/**
 * Cria a conta da proprietária na primeira inicialização, a partir de variáveis de ambiente
 * ({@code APP_ADMIN_EMAIL}, {@code APP_ADMIN_NAME}, {@code APP_ADMIN_INITIAL_PASSWORD}).
 * Só age se não existir nenhuma conta; a senha inicial é provisória e precisa ser trocada no primeiro acesso.
 * Não há endpoint público de cadastro.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy policy;
    private final AuditService audit;
    private final AuthProperties properties;
    private final Clock clock;

    public AdminBootstrap(AdminUserRepository users, PasswordEncoder passwordEncoder, PasswordPolicy policy,
            AuditService audit, AuthProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.policy = policy;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfNoneExists(properties.bootstrap());
    }

    /** @return {@code true} se a conta foi criada */
    @Transactional
    public boolean createIfNoneExists(AuthProperties.Bootstrap bootstrap) {
        if (users.count() > 0) {
            return false;
        }
        if (!bootstrap.isConfigured()) {
            log.warn("Nenhuma conta administrativa cadastrada. Defina APP_ADMIN_EMAIL e APP_ADMIN_INITIAL_PASSWORD "
                    + "e reinicie a aplicação para criá-la.");
            return false;
        }
        String email = AdminUser.normalizeEmail(bootstrap.email());
        policy.validate(bootstrap.password(), email, "APP_ADMIN_INITIAL_PASSWORD");
        String name = bootstrap.name() == null || bootstrap.name().isBlank() ? "Proprietária" : bootstrap.name();

        AdminUser user = new AdminUser(email, name, passwordEncoder.encode(bootstrap.password()), clock.instant());
        user.requirePasswordChange();
        users.save(user);
        audit.record(ActorType.SYSTEM, null, "ADMIN_BOOTSTRAPPED", "admin_user", user.getId(), Map.of("mustChangePassword", true));
        log.info("Conta administrativa inicial criada. A senha provisória deverá ser trocada no primeiro acesso.");
        return true;
    }
}
