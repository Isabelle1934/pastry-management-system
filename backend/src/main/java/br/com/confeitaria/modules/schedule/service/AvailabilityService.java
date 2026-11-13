package br.com.confeitaria.modules.schedule.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.config.AppProperties;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.AvailableDay;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.CalendarDay;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.CalendarSlot;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.PublicAvailability;
import br.com.confeitaria.modules.schedule.entity.ScheduleBlock;
import br.com.confeitaria.modules.schedule.entity.WeeklySlot;
import br.com.confeitaria.modules.schedule.repository.ScheduleBlockRepository;
import br.com.confeitaria.modules.schedule.repository.SlotOccupancyRepository;
import br.com.confeitaria.modules.schedule.repository.SlotOccupancyRepository.SlotKey;
import br.com.confeitaria.modules.schedule.repository.WeeklySlotRepository;
import br.com.confeitaria.modules.settings.entity.BakerySettings;
import br.com.confeitaria.modules.settings.service.SettingsService;

/**
 * Gera os horários a partir do modelo semanal e aplica, nesta ordem: bloqueios, antecedência mínima,
 * limite de dias à frente e vagas já ocupadas (somente pedidos com sinal confirmado ocupam vaga).
 */
@Service
public class AvailabilityService {

    static final int MAX_RANGE_DAYS = 62;

    private final WeeklySlotRepository slots;
    private final ScheduleBlockRepository blocks;
    private final SlotOccupancyRepository occupancy;
    private final SettingsService settingsService;
    private final AppProperties properties;
    private final Clock clock;

    public AvailabilityService(WeeklySlotRepository slots, ScheduleBlockRepository blocks,
            SlotOccupancyRepository occupancy, SettingsService settingsService, AppProperties properties, Clock clock) {
        this.slots = slots;
        this.blocks = blocks;
        this.occupancy = occupancy;
        this.settingsService = settingsService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Visão completa para o painel: capacidade, ocupação e motivo de cada horário estar indisponível. */
    @Transactional(readOnly = true)
    public List<CalendarDay> calendar(LocalDate from, LocalDate to) {
        validateRange(from, to);
        Context ctx = context(from, to);
        List<CalendarDay> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            ScheduleBlock wholeDay = ctx.wholeDayBlocks.get(date);
            List<CalendarSlot> daySlots = new ArrayList<>();
            for (WeeklySlot slot : ctx.slotsByDay.getOrDefault(date.getDayOfWeek().getValue(), List.of())) {
                daySlots.add(evaluate(ctx, date, slot));
            }
            days.add(new CalendarDay(date, date.getDayOfWeek().getValue(), wholeDay != null,
                    wholeDay == null ? null : wholeDay.getReason(), daySlots));
        }
        return days;
    }

    /** Somente datas e horários que a cliente pode escolher agora, sem revelar contagens. */
    @Transactional(readOnly = true)
    public PublicAvailability publicAvailability(LocalDate from, LocalDate to) {
        BakerySettings settings = settingsService.current();
        LocalDate today = today();
        LocalDate start = from == null || from.isBefore(today) ? today : from;
        LocalDate lastAllowed = today.plusDays(settings.getMaxBookingDaysAhead());
        LocalDate end = to == null ? start.plusDays(MAX_RANGE_DAYS - 1) : to;
        if (end.isAfter(lastAllowed)) {
            end = lastAllowed;
        }
        if (end.isBefore(start)) {
            return new PublicAvailability(start, start, List.of());
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
            end = start.plusDays(MAX_RANGE_DAYS - 1);
        }

        List<AvailableDay> days = calendar(start, end).stream()
                .map(day -> new AvailableDay(day.date(),
                        day.slots().stream().filter(CalendarSlot::bookable).map(CalendarSlot::time).toList()))
                .filter(day -> !day.times().isEmpty())
                .toList();
        return new PublicAvailability(start, end, days);
    }

    /** Usado ao receber e editar solicitações: o horário escolhido ainda pode ser solicitado? */
    @Transactional(readOnly = true)
    public boolean isBookable(LocalDate date, LocalTime time) {
        Context ctx = context(date, date);
        return ctx.slotsByDay.getOrDefault(date.getDayOfWeek().getValue(), List.of()).stream()
                .filter(slot -> slot.getStartTime().equals(time))
                .findFirst()
                .map(slot -> evaluate(ctx, date, slot).bookable())
                .orElse(false);
    }

    private CalendarSlot evaluate(Context ctx, LocalDate date, WeeklySlot slot) {
        LocalTime time = slot.getStartTime();
        ScheduleBlock wholeDay = ctx.wholeDayBlocks.get(date);
        ScheduleBlock timeBlock = ctx.timeBlocks.get(new SlotKey(date, time));
        boolean blocked = wholeDay != null || timeBlock != null;
        String blockReason = wholeDay != null ? wholeDay.getReason() : timeBlock != null ? timeBlock.getReason() : null;
        int reserved = ctx.reserved.getOrDefault(new SlotKey(date, time), 0);
        Instant slotInstant = date.atTime(time).atZone(ctx.zone).toInstant();
        boolean tooSoon = slotInstant.isBefore(ctx.earliest);
        boolean beyondLimit = date.isAfter(ctx.lastDate);
        boolean full = slot.getCapacity() != null && reserved >= slot.getCapacity();
        boolean bookable = !blocked && !tooSoon && !beyondLimit && !full;
        return new CalendarSlot(time.toString(), slot.getCapacity(), reserved, blocked, blockReason, tooSoon,
                beyondLimit, bookable);
    }

    private Context context(LocalDate from, LocalDate to) {
        BakerySettings settings = settingsService.current();
        ZoneId zone = properties.timezone();
        Instant now = clock.instant();

        Map<Integer, List<WeeklySlot>> slotsByDay = new HashMap<>();
        for (WeeklySlot slot : slots.findAllByOrderByDayOfWeekAscStartTimeAsc()) {
            if (slot.isActive()) {
                slotsByDay.computeIfAbsent(slot.getDayOfWeek().getValue(), d -> new ArrayList<>()).add(slot);
            }
        }
        Map<LocalDate, ScheduleBlock> wholeDayBlocks = new HashMap<>();
        Map<SlotKey, ScheduleBlock> timeBlocks = new HashMap<>();
        for (ScheduleBlock block : blocks.findAllByBlockDateBetweenOrderByBlockDateAscStartTimeAsc(from, to)) {
            if (block.isWholeDay()) {
                wholeDayBlocks.put(block.getBlockDate(), block);
            } else {
                timeBlocks.put(new SlotKey(block.getBlockDate(), block.getStartTime()), block);
            }
        }
        return new Context(zone, now.plus(settings.getMinLeadTimeHours(), ChronoUnit.HOURS),
                LocalDate.ofInstant(now, zone).plusDays(settings.getMaxBookingDaysAhead()), slotsByDay,
                wholeDayBlocks, timeBlocks, occupancy.reservedBetween(from, to));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new FieldValidationException("from", "Informe o período.");
        }
        if (to.isBefore(from)) {
            throw new FieldValidationException("to", "A data final deve ser igual ou posterior à inicial.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new FieldValidationException("to", "Consulte no máximo " + MAX_RANGE_DAYS + " dias por vez.");
        }
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), properties.timezone());
    }

    private record Context(ZoneId zone, Instant earliest, LocalDate lastDate, Map<Integer, List<WeeklySlot>> slotsByDay,
            Map<LocalDate, ScheduleBlock> wholeDayBlocks, Map<SlotKey, ScheduleBlock> timeBlocks,
            Map<SlotKey, Integer> reserved) {
    }
}
