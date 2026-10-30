package br.com.confeitaria.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.confeitaria.support.IntegrationTest;

@IntegrationTest
class FlywayMigrationIntegrationTests {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void allMigrationsAreApplied() {
        var migrations = flyway.info().all();
        assertThat(migrations).isNotEmpty();
        assertThat(Arrays.stream(migrations).map(m -> m.getState()))
                .allMatch(state -> state == MigrationState.SUCCESS);
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void baseTablesExist() {
        List<String> tables = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public' order by table_name
                """, String.class);

        assertThat(tables).contains(
                "admin_user", "password_reset_token", "audit_log",
                "spring_session", "spring_session_attributes", "shedlock");
    }
}
