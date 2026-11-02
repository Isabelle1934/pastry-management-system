package br.com.confeitaria.modules.schedule;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.confeitaria.modules.auth.service.AuthRateLimiter;
import br.com.confeitaria.modules.schedule.service.AvailabilityService;
import br.com.confeitaria.modules.schedule.service.ReservationService;
import br.com.confeitaria.support.AuthTestSupport;
import br.com.confeitaria.support.IntegrationTest;
import br.com.confeitaria.support.MutableClock;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

/**
 * "Agora" é segunda-feira 02/11/2026 às 09:00 em São Paulo (12:00 UTC).
 * Antecedência mínima de 24 h: o primeiro horário possível é terça 03/11 às 09:00.
 */
@IntegrationTest
class ScheduleIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 3);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 4);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTestSupport support;

    @Autowired
    private AuthRateLimiter rateLimiter;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private ReservationService reservations;

    @Autowired
    private AvailabilityService availability;

    @Autowired
    private JsonMapper json;

    private ScheduleTestSupport schedule;
    private Cookie session;

    @BeforeEach
    void setUp() throws Exception {
        rateLimiter.clear();
        clock.set(NOW);
        schedule = new ScheduleTestSupport(jdbc);
        schedule.reset(24, 90);
        session = login(mockMvc, support.createUser().getEmail(), PASSWORD);
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            String body) throws Exception {
        return mockMvc.perform(builder.with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // --- Disponibilidade ---

    @Test
    void availabilityAppliesLeadTimeInactiveSlotsAndWeekdays() throws Exception {
        schedule.slot(2, "08:00", 2, true);   // terça 08:00 — antes da antecedência mínima
        schedule.slot(2, "14:00", 1, true);   // terça 14:00 — disponível
        schedule.slot(3, "10:00", 1, true);   // quarta 10:00 — disponível
        schedule.slot(7, "10:00", 1, false);  // domingo — inativo

        mockMvc.perform(get("/api/public/availability").param("from", "2026-11-02").param("to", "2026-11-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days", hasSize(2)))
                .andExpect(jsonPath("$.days[0].date").value("2026-11-03"))
                .andExpect(jsonPath("$.days[0].times", contains("14:00")))
                .andExpect(jsonPath("$.days[1].date").value("2026-11-04"))
                .andExpect(jsonPath("$.days[1].times", contains("10:00")))
                .andExpect(jsonPath("$.days[0].capacity").doesNotExist());
    }

    @Test
    void blocksAndFullSlotsAreNotOffered() throws Exception {
        schedule.slot(2, "14:00", 1, true);
        schedule.slot(2, "16:00", 1, true);
        schedule.slot(3, "10:00", 1, true);
        schedule.block(TUESDAY, "14:00");
        schedule.block(WEDNESDAY, null);
        tx.executeWithoutResult(s -> reservations.reserve(42L, TUESDAY, LocalTime.of(16, 0)));

        mockMvc.perform(get("/api/public/availability").param("from", "2026-11-03").param("to", "2026-11-04"))
                .andExpect(jsonPath("$.days", hasSize(0)));

        mockMvc.perform(get("/api/admin/schedule/calendar").cookie(session)
                        .param("from", "2026-11-03").param("to", "2026-11-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slots[0].time").value("14:00"))
                .andExpect(jsonPath("$[0].slots[0].blocked").value(true))
                .andExpect(jsonPath("$[0].slots[1].reserved").value(1))
                .andExpect(jsonPath("$[0].slots[1].capacity").value(1))
                .andExpect(jsonPath("$[0].slots[1].bookable").value(false))
                .andExpect(jsonPath("$[1].blockedAllDay").value(true));
    }

    @Test
    void solicitationsDoNotConsumeCapacityOnlyConfirmationsDo() {
        schedule.slot(2, "14:00", 1, true);
        assertThat(availability.isBookable(TUESDAY, LocalTime.of(14, 0))).isTrue();

        tx.executeWithoutResult(s -> reservations.reserve(7L, TUESDAY, LocalTime.of(14, 0)));

        assertThat(availability.isBookable(TUESDAY, LocalTime.of(14, 0))).isFalse();
        assertThat(availability.isBookable(TUESDAY, LocalTime.of(15, 0))).as("horário inexistente").isFalse();
    }

    @Test
    void maxBookingDaysAheadLimitsTheOffer() throws Exception {
        schedule.reset(24, 7);
        schedule.slot(2, "14:00", 1, true);

        mockMvc.perform(get("/api/public/availability"))
                .andExpect(jsonPath("$.to").value("2026-11-09"))
                .andExpect(jsonPath("$.days[*].date", contains("2026-11-03")));
    }

    @Test
    void localDateIsUsedNearMidnight() throws Exception {
        // 02:30 UTC de terça = 23:30 de segunda em São Paulo: "hoje" ainda é segunda.
        clock.set(Instant.parse("2026-11-03T02:30:00Z"));
        schedule.reset(0, 90);
        schedule.slot(1, "23:45", 1, true);

        mockMvc.perform(get("/api/public/availability").param("to", "2026-11-02"))
                .andExpect(jsonPath("$.from").value("2026-11-02"))
                .andExpect(jsonPath("$.days[0].times", contains("23:45")));
        send(post("/api/admin/schedule/blocks"), "{\"date\":\"2026-11-02\"}").andExpect(status().isCreated());
        send(post("/api/admin/schedule/blocks"), "{\"date\":\"2026-11-01\"}").andExpect(status().isBadRequest());
    }

    @Test
    void calendarRangeIsLimited() throws Exception {
        mockMvc.perform(get("/api/admin/schedule/calendar").cookie(session)
                        .param("from", "2026-11-01").param("to", "2027-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));
    }

    // --- Configuração pelo painel ---

    @Test
    void scheduleManagementRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/admin/schedule/slots")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/public/availability")).andExpect(status().isOk());
    }

    @Test
    void ownerManagesWeeklySlots() throws Exception {
        String body = send(post("/api/admin/schedule/slots"), "{\"dayOfWeek\":5,\"startTime\":\"14:30\",\"capacity\":3}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").value("14:30"))
                .andExpect(jsonPath("$.dayOfWeek").value(5))
                .andReturn().getResponse().getContentAsString();
        var created = json.readTree(body);
        long id = created.get("id").asLong();

        send(post("/api/admin/schedule/slots"), "{\"dayOfWeek\":5,\"startTime\":\"14:30\",\"capacity\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SLOT"));
        send(post("/api/admin/schedule/slots"), "{\"dayOfWeek\":8,\"startTime\":\"25:00\",\"capacity\":0}")
                .andExpect(status().isBadRequest());

        send(put("/api/admin/schedule/slots/" + id),
                "{\"capacity\":5,\"active\":false,\"version\":" + created.get("version").asLong() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(5))
                .andExpect(jsonPath("$.active").value(false));
        send(put("/api/admin/schedule/slots/" + id),
                "{\"capacity\":6,\"active\":true,\"version\":" + created.get("version").asLong() + "}")
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/admin/schedule/slots/" + id).with(csrf()).cookie(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/schedule/slots").cookie(session)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ownerManagesBlocksAndIsWarnedAboutConfirmedOrders() throws Exception {
        schedule.slot(2, "14:00", 2, true);
        tx.executeWithoutResult(s -> reservations.reserve(99L, TUESDAY, LocalTime.of(14, 0)));

        String body = send(post("/api/admin/schedule/blocks"),
                "{\"date\":\"2026-11-03\",\"startTime\":\"14:00\",\"reason\":\"Feriado\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.confirmedOrders").value(1))
                .andExpect(jsonPath("$.reason").value("Feriado"))
                .andReturn().getResponse().getContentAsString();

        send(post("/api/admin/schedule/blocks"), "{\"date\":\"2026-11-03\",\"startTime\":\"14:00\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_BLOCK"));
        send(post("/api/admin/schedule/blocks"), "{\"date\":\"2026-11-03\",\"startTime\":\"09:00\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startTime"));
        send(post("/api/admin/schedule/blocks"), "{\"date\":\"2026-11-03\"}").andExpect(status().isCreated());
        send(post("/api/admin/schedule/blocks"), "{\"date\":\"2026-11-03\"}").andExpect(status().isConflict());

        mockMvc.perform(get("/api/admin/schedule/blocks").cookie(session)).andExpect(jsonPath("$", hasSize(2)));
        long id = json.readTree(body).get("id").asLong();
        mockMvc.perform(delete("/api/admin/schedule/blocks/" + id).with(csrf()).cookie(session))
                .andExpect(status().isNoContent());
        assertThat(schedule.activeReservations(TUESDAY, "14:00")).as("bloqueio não cancela pedidos").isEqualTo(1);
    }

    @Test
    void ownerUpdatesSchedulingRules() throws Exception {
        String body = mockMvc.perform(get("/api/admin/schedule/rules").cookie(session))
                .andExpect(jsonPath("$.minLeadTimeHours").value(24))
                .andReturn().getResponse().getContentAsString();
        long version = json.readTree(body).get("version").asLong();

        send(put("/api/admin/schedule/rules"), "{\"minLeadTimeHours\":48,\"maxBookingDaysAhead\":30,\"version\":" + version + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minLeadTimeHours").value(48));
        send(put("/api/admin/schedule/rules"), "{\"minLeadTimeHours\":12,\"maxBookingDaysAhead\":30,\"version\":" + version + "}")
                .andExpect(status().isConflict());
        send(put("/api/admin/schedule/rules"), "{\"minLeadTimeHours\":-1,\"maxBookingDaysAhead\":0,\"version\":" + version + "}")
                .andExpect(status().isBadRequest());
    }
}
