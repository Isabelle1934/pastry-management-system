package br.com.confeitaria.modules.schedule.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Contador de vagas ocupadas por horário. Usa SQL direto porque o controle de concorrência
 * depende de operações atômicas do PostgreSQL (ADR-004).
 */
@Repository
public class SlotOccupancyRepository {

    public record SlotKey(LocalDate date, LocalTime time) {
    }

    private final JdbcTemplate jdbc;

    public SlotOccupancyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Ocupa uma vaga se ainda houver. O UPDATE condicional trava a linha do horário: confirmações
     * simultâneas são serializadas pelo banco e cada uma reavalia o contador já atualizado.
     *
     * @return {@code true} se a vaga foi ocupada; {@code false} se o horário estava lotado
     */
    public boolean tryIncrement(LocalDate date, LocalTime time, Integer capacity) {
        jdbc.update("""
                insert into slot_occupancy (slot_date, slot_time, reserved_count) values (?, ?, 0)
                on conflict (slot_date, slot_time) do nothing
                """, date, time);
        int updated = jdbc.update("""
                update slot_occupancy set reserved_count = reserved_count + 1, updated_at = now()
                where slot_date = ? and slot_time = ? and reserved_count < ?
                """, date, time, capacity == null ? Integer.MAX_VALUE : capacity);
        return updated == 1;
    }

    public void decrement(LocalDate date, LocalTime time) {
        jdbc.update("""
                update slot_occupancy set reserved_count = reserved_count - 1, updated_at = now()
                where slot_date = ? and slot_time = ? and reserved_count > 0
                """, date, time);
    }

    /** Quantos pedidos confirmados existem na data (e no horário, se informado). */
    public int activeReservations(LocalDate date, LocalTime time) {
        Integer count = time == null
                ? jdbc.queryForObject("select count(*) from slot_reservation where status = 'ACTIVE' and slot_date = ?",
                        Integer.class, date)
                : jdbc.queryForObject("""
                        select count(*) from slot_reservation
                        where status = 'ACTIVE' and slot_date = ? and slot_time = ?
                        """, Integer.class, date, time);
        return count == null ? 0 : count;
    }

    public Map<SlotKey, Integer> reservedBetween(LocalDate from, LocalDate to) {
        Map<SlotKey, Integer> result = new HashMap<>();
        jdbc.query("""
                select slot_date, slot_time, reserved_count from slot_occupancy
                where slot_date between ? and ? and reserved_count > 0
                """, rs -> {
            result.put(new SlotKey(rs.getObject(1, LocalDate.class), rs.getObject(2, LocalTime.class)), rs.getInt(3));
        }, from, to);
        return result;
    }
}
