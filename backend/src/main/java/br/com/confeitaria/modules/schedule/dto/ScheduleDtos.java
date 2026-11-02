package br.com.confeitaria.modules.schedule.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import br.com.confeitaria.modules.schedule.entity.ScheduleBlock;
import br.com.confeitaria.modules.schedule.entity.WeeklySlot;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos da agenda. Horários trafegam como "HH:mm" e datas como "AAAA-MM-DD". */
public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    // --- Modelo semanal ---

    public record SlotRequest(
            @NotNull(message = "Informe o dia da semana.")
            @Min(value = 1, message = "Dia inválido.") @Max(value = 7, message = "Dia inválido.") Integer dayOfWeek,
            @NotNull(message = "Informe o horário.") LocalTime startTime,
            @NotNull(message = "Informe o limite de pedidos.")
            @Min(value = 1, message = "Mínimo de 1 pedido.") @Max(value = 100, message = "Máximo de 100 pedidos.") Integer capacity) {
    }

    public record SlotUpdateRequest(
            @NotNull(message = "Informe o limite de pedidos.")
            @Min(value = 1, message = "Mínimo de 1 pedido.") @Max(value = 100, message = "Máximo de 100 pedidos.") Integer capacity,
            @NotNull(message = "Informe se o horário está ativo.") Boolean active,
            @NotNull(message = "Versão obrigatória.") Long version) {
    }

    public record SlotResponse(Long id, int dayOfWeek, String startTime, int capacity, boolean active, long version) {

        public static SlotResponse from(WeeklySlot slot) {
            return new SlotResponse(slot.getId(), slot.getDayOfWeek().getValue(), slot.getStartTime().toString(),
                    slot.getCapacity(), slot.isActive(), slot.getVersion());
        }
    }

    // --- Bloqueios ---

    public record BlockRequest(
            @NotNull(message = "Informe a data.") LocalDate date,
            LocalTime startTime,
            @Size(max = 200, message = "Máximo de 200 caracteres.") String reason) {
    }

    /** {@code confirmedOrders}: pedidos já confirmados no período bloqueado (não são cancelados automaticamente). */
    public record BlockResponse(Long id, LocalDate date, String startTime, String reason, int confirmedOrders) {

        public static BlockResponse from(ScheduleBlock block, int confirmedOrders) {
            return new BlockResponse(block.getId(), block.getBlockDate(),
                    block.getStartTime() == null ? null : block.getStartTime().toString(), block.getReason(),
                    confirmedOrders);
        }
    }

    // --- Regras gerais ---

    public record RulesRequest(
            @NotNull(message = "Informe a antecedência mínima.")
            @Min(value = 0, message = "Use 0 ou mais.") @Max(value = 720, message = "Máximo de 720 horas (30 dias).")
            Integer minLeadTimeHours,
            @NotNull(message = "Informe até quantos dias à frente.")
            @Min(value = 1, message = "Mínimo de 1 dia.") @Max(value = 365, message = "Máximo de 365 dias.")
            Integer maxBookingDaysAhead,
            @NotNull(message = "Versão obrigatória.") Long version) {
    }

    public record RulesResponse(int minLeadTimeHours, int maxBookingDaysAhead, long version) {
    }

    // --- Visão da agenda (painel) ---

    /**
     * @param bookable horário pode receber novas solicitações agora (dentro da antecedência e do prazo,
     *                 sem bloqueio e com vaga)
     */
    public record CalendarSlot(String time, int capacity, int reserved, boolean blocked, String blockReason,
            boolean tooSoon, boolean beyondLimit, boolean bookable) {
    }

    public record CalendarDay(LocalDate date, int dayOfWeek, boolean blockedAllDay, String blockReason,
            List<CalendarSlot> slots) {
    }

    // --- Área pública: só o que pode ser escolhido, sem contagens ---

    public record AvailableDay(LocalDate date, List<String> times) {
    }

    public record PublicAvailability(LocalDate from, LocalDate to, List<AvailableDay> days) {
    }
}
