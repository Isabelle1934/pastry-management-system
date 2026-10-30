package br.com.confeitaria.common.audit;

import java.util.List;

import org.springframework.data.repository.Repository;

/** Repositório somente leitura/inserção — não expõe update nem delete. */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtAscIdAsc(String entityType, String entityId);
}
