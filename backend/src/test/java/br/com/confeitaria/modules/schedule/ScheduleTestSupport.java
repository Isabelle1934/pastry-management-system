package br.com.confeitaria.modules.schedule;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.jdbc.core.JdbcTemplate;

/** Prepara a agenda diretamente no banco para os testes (a agenda é estado global da aplicação). */
final class ScheduleTestSupport {

    private final JdbcTemplate jdbc;

    ScheduleTestSupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void reset(int minLeadTimeHours, int maxBookingDaysAhead) {
        jdbc.update("delete from slot_reservation");
        jdbc.update("delete from slot_occupancy");
        jdbc.update("delete from schedule_block");
        jdbc.update("delete from weekly_slot");
        jdbc.update("update bakery_settings set min_lead_time_hours = ?, max_booking_days_ahead = ?",
                minLeadTimeHours, maxBookingDaysAhead);
    }

    void slot(int dayOfWeek, String time, int capacity, boolean active) {
        jdbc.update("""
                insert into weekly_slot (day_of_week, start_time, capacity, active, created_at, updated_at)
                values (?, ?, ?, ?, now(), now())
                """, dayOfWeek, LocalTime.parse(time), capacity, active);
    }

    void block(LocalDate date, String time) {
        jdbc.update("insert into schedule_block (block_date, start_time, reason, created_at) values (?, ?, 'teste', now())",
                date, time == null ? null : LocalTime.parse(time));
    }

    int reserved(LocalDate date, String time) {
        Integer count = jdbc.queryForObject("""
                select coalesce(max(reserved_count), 0) from slot_occupancy where slot_date = ? and slot_time = ?
                """, Integer.class, date, LocalTime.parse(time));
        return count == null ? 0 : count;
    }

    int activeReservations(LocalDate date, String time) {
        Integer count = jdbc.queryForObject("""
                select count(*) from slot_reservation where status = 'ACTIVE' and slot_date = ? and slot_time = ?
                """, Integer.class, date, LocalTime.parse(time));
        return count == null ? 0 : count;
    }
}
