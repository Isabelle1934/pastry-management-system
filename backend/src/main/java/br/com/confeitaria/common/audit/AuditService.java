package br.com.confeitaria.common.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra ações administrativas e do sistema. Participa da transação de quem chama:
 * se a operação principal for desfeita, o registro de auditoria também é.
 * Não registre senhas, tokens ou dados pessoais desnecessários em {@code details}.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog record(ActorType actorType, Long actorId, String action, String entityType, Object entityId,
            Map<String, Object> details) {
        String id = entityId == null ? null : entityId.toString();
        return repository.save(new AuditLog(actorType, actorId, action, entityType, id, details, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> historyOf(String entityType, Object entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByCreatedAtAscIdAsc(entityType, entityId.toString());
    }
}
