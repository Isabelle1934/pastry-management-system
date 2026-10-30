package br.com.confeitaria.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.confeitaria.support.IntegrationTest;

@IntegrationTest
class AuditServiceIntegrationTests {

    @Autowired
    private AuditService auditService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void recordsEntryWithJsonDetails() {
        String entityId = UUID.randomUUID().toString();

        tx.executeWithoutResult(status -> auditService.record(ActorType.OWNER, 1L, "SETTINGS_UPDATED", "bakery_settings",
                entityId, Map.of("field", "quoteValidityHours", "from", 48, "to", 72)));

        var history = auditService.historyOf("bakery_settings", entityId);
        assertThat(history).hasSize(1);
        AuditLog entry = history.getFirst();
        assertThat(entry.getActorType()).isEqualTo(ActorType.OWNER);
        assertThat(entry.getAction()).isEqualTo("SETTINGS_UPDATED");
        assertThat(entry.getDetails())
                .containsEntry("field", "quoteValidityHours")
                .containsEntry("from", 48)
                .containsEntry("to", 72);
        assertThat(entry.getCreatedAt()).isNotNull();
    }

    @Test
    void entryIsRolledBackTogetherWithTheMainOperation() {
        String entityId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            auditService.record(ActorType.SYSTEM, null, "SOMETHING", "test_entity", entityId, null);
            throw new IllegalStateException("falha na operação principal");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(auditService.historyOf("test_entity", entityId)).isEmpty();
    }

    @Test
    void requiresAnExistingTransaction() {
        assertThatThrownBy(() -> auditService.record(ActorType.SYSTEM, null, "X", "test_entity", "1", null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void databaseRejectsUpdateAndDeleteOfAuditEntries() {
        String entityId = UUID.randomUUID().toString();
        tx.executeWithoutResult(status -> auditService.record(ActorType.SYSTEM, null, "CREATED", "test_entity", entityId, null));

        assertThatThrownBy(() -> jdbc.update("update audit_log set action = 'ALTERADO' where entity_id = ?", entityId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("imutáveis");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log where entity_id = ?", entityId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("imutáveis");

        assertThat(auditService.historyOf("test_entity", entityId)).hasSize(1);
    }
}
