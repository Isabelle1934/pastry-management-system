package br.com.confeitaria.modules.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/** Prepara a agenda diretamente no banco para os testes (a agenda é estado global da aplicação). */
public final class ScheduleTestSupport {

    private static final String CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private final JdbcTemplate jdbc;

    public ScheduleTestSupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void reset(int minLeadTimeHours, int maxBookingDaysAhead) {
        jdbc.update("delete from slot_reservation");
        jdbc.update("delete from slot_occupancy");
        jdbc.update("delete from schedule_block");
        jdbc.update("delete from weekly_slot");
        jdbc.update("update bakery_settings set min_lead_time_hours = ?, max_booking_days_ahead = ?",
                minLeadTimeHours, maxBookingDaysAhead);
    }

    public void slot(int dayOfWeek, String time, int capacity, boolean active) {
        jdbc.update("""
                insert into weekly_slot (day_of_week, start_time, capacity, active, created_at, updated_at)
                values (?, ?, ?, ?, now(), now())
                """, dayOfWeek, LocalTime.parse(time), capacity, active);
    }

    public void block(LocalDate date, String time) {
        jdbc.update("insert into schedule_block (block_date, start_time, reason, created_at) values (?, ?, 'teste', now())",
                date, time == null ? null : LocalTime.parse(time));
    }

    /** Cria um pedido mínimo direto no banco (reservas de vaga exigem um pedido existente). */
    public long newOrder() {
        Long sizeId = jdbc.queryForObject("""
                insert into cake_size (name, base_price, max_flavors, max_fillings, created_at, updated_at)
                values (?, 100, 1, 1, now(), now()) returning id
                """, Long.class, "Tamanho teste " + UUID.randomUUID());
        Long id = jdbc.queryForObject("""
                insert into cake_order (tracking_code, customer_name, customer_whatsapp, pickup_date, pickup_time,
                    size_id, size_name_snapshot, commercial_status, production_status, payment_status,
                    created_at, updated_at)
                values (?, 'Cliente Teste', '+5511999998888', current_date, '14:00', ?, 'Teste',
                    'AWAITING_DEPOSIT', 'NOT_STARTED', 'NONE', now(), now())
                returning id
                """, Long.class, randomCode(), sizeId);
        return id;
    }

    public int reserved(LocalDate date, String time) {
        Integer count = jdbc.queryForObject("""
                select coalesce(max(reserved_count), 0) from slot_occupancy where slot_date = ? and slot_time = ?
                """, Integer.class, date, LocalTime.parse(time));
        return count == null ? 0 : count;
    }

    public int activeReservations(LocalDate date, String time) {
        Integer count = jdbc.queryForObject("""
                select count(*) from slot_reservation where status = 'ACTIVE' and slot_date = ? and slot_time = ?
                """, Integer.class, date, LocalTime.parse(time));
        return count == null ? 0 : count;
    }

    private static String randomCode() {
        var random = new java.security.SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            code.append(CROCKFORD.charAt(random.nextInt(CROCKFORD.length())));
        }
        return code.toString();
    }
}
