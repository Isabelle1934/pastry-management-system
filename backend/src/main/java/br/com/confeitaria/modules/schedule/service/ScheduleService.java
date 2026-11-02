package br.com.confeitaria.modules.schedule.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.audit.AuditService;
import br.com.confeitaria.common.audit.Changes;
import br.com.confeitaria.common.exception.ConflictException;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.common.exception.NotFoundException;
import br.com.confeitaria.common.persistence.Versions;
import br.com.confeitaria.config.AppProperties;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.BlockRequest;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.BlockResponse;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.RulesRequest;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.RulesResponse;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.SlotRequest;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.SlotResponse;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.SlotUpdateRequest;
import br.com.confeitaria.modules.schedule.entity.ScheduleBlock;
import br.com.confeitaria.modules.schedule.entity.WeeklySlot;
import br.com.confeitaria.modules.schedule.repository.ScheduleBlockRepository;
import br.com.confeitaria.modules.schedule.repository.SlotOccupancyRepository;
import br.com.confeitaria.modules.schedule.repository.WeeklySlotRepository;
import br.com.confeitaria.modules.settings.entity.BakerySettings;
import br.com.confeitaria.modules.settings.service.SettingsService;

/** Configuração da agenda pela proprietária: horários semanais, bloqueios e regras gerais. */
@Service
public class ScheduleService {

    private final WeeklySlotRepository slots;
    private final ScheduleBlockRepository blocks;
    private final SlotOccupancyRepository occupancy;
    private final SettingsService settingsService;
    private final AuditService audit;
    private final AppProperties properties;
    private final Clock clock;

    public ScheduleService(WeeklySlotRepository slots, ScheduleBlockRepository blocks, SlotOccupancyRepository occupancy,
            SettingsService settingsService, AuditService audit, AppProperties properties, Clock clock) {
        this.slots = slots;
        this.blocks = blocks;
        this.occupancy = occupancy;
        this.settingsService = settingsService;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    // --- Modelo semanal ---

    @Transactional(readOnly = true)
    public List<SlotResponse> listSlots() {
        return slots.findAllByOrderByDayOfWeekAscStartTimeAsc().stream().map(SlotResponse::from).toList();
    }

    @Transactional
    public SlotResponse createSlot(SlotRequest request, Long actorId) {
        DayOfWeek day = DayOfWeek.of(request.dayOfWeek());
        LocalTime time = request.startTime().withSecond(0).withNano(0);
        if (slots.existsByDayOfWeekAndStartTime((short) day.getValue(), time)) {
            throw new ConflictException("DUPLICATE_SLOT", "Já existe um horário às " + time + " nesse dia.");
        }
        WeeklySlot slot = slots.save(new WeeklySlot(day, time, request.capacity()));
        audit.record(ActorType.OWNER, actorId, "SLOT_CREATED", "weekly_slot", slot.getId(),
                Map.of("dayOfWeek", day.getValue(), "startTime", time.toString(), "capacity", request.capacity()));
        return SlotResponse.from(slot);
    }

    /**
     * Reduzir o limite abaixo do que já está ocupado é permitido: os pedidos confirmados são mantidos
     * e o horário apenas deixa de aceitar novas confirmações.
     */
    @Transactional
    public SlotResponse updateSlot(Long id, SlotUpdateRequest request, Long actorId) {
        WeeklySlot slot = findSlot(id);
        Versions.require(slot.getVersion(), request.version());
        Changes changes = new Changes()
                .track("capacity", slot.getCapacity(), request.capacity())
                .track("active", slot.isActive(), request.active());
        slot.update(request.capacity(), request.active());
        slots.flush();
        if (!changes.isEmpty()) {
            audit.record(ActorType.OWNER, actorId, "SLOT_UPDATED", "weekly_slot", id, changes.asMap());
        }
        return SlotResponse.from(slot);
    }

    /** Remove o horário do modelo. Pedidos já confirmados nesse horário continuam válidos. */
    @Transactional
    public void deleteSlot(Long id, Long actorId) {
        WeeklySlot slot = findSlot(id);
        slots.delete(slot);
        audit.record(ActorType.OWNER, actorId, "SLOT_DELETED", "weekly_slot", id,
                Map.of("dayOfWeek", slot.getDayOfWeek().getValue(), "startTime", slot.getStartTime().toString()));
    }

    // --- Bloqueios ---

    @Transactional(readOnly = true)
    public List<BlockResponse> listUpcomingBlocks() {
        return blocks.findAllByBlockDateGreaterThanEqualOrderByBlockDateAscStartTimeAsc(today()).stream()
                .map(b -> BlockResponse.from(b, occupancy.activeReservations(b.getBlockDate(), b.getStartTime())))
                .toList();
    }

    /** Bloquear não cancela pedidos já confirmados: a resposta informa quantos existem para a proprietária decidir. */
    @Transactional
    public BlockResponse createBlock(BlockRequest request, Long actorId) {
        LocalDate date = request.date();
        if (date.isBefore(today())) {
            throw new FieldValidationException("date", "Escolha uma data a partir de hoje.");
        }
        LocalTime time = request.startTime() == null ? null : request.startTime().withSecond(0).withNano(0);
        if (time != null && !slots.existsByDayOfWeekAndStartTime((short) date.getDayOfWeek().getValue(), time)) {
            throw new FieldValidationException("startTime", "Não há horário de retirada às " + time + " nesse dia da semana.");
        }
        boolean duplicate = time == null ? blocks.existsByBlockDateAndStartTimeIsNull(date)
                : blocks.existsByBlockDateAndStartTime(date, time);
        if (duplicate) {
            throw new ConflictException("DUPLICATE_BLOCK", "Esse bloqueio já existe.");
        }
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        ScheduleBlock block = blocks.save(new ScheduleBlock(date, time, reason, clock.instant()));
        audit.record(ActorType.OWNER, actorId, "BLOCK_CREATED", "schedule_block", block.getId(),
                time == null ? Map.of("date", date.toString()) : Map.of("date", date.toString(), "startTime", time.toString()));
        return BlockResponse.from(block, occupancy.activeReservations(date, time));
    }

    @Transactional
    public void deleteBlock(Long id, Long actorId) {
        ScheduleBlock block = blocks.findById(id).orElseThrow(() -> new NotFoundException("Bloqueio não encontrado."));
        blocks.delete(block);
        audit.record(ActorType.OWNER, actorId, "BLOCK_DELETED", "schedule_block", id,
                Map.of("date", block.getBlockDate().toString()));
    }

    // --- Regras gerais ---

    @Transactional(readOnly = true)
    public RulesResponse getRules() {
        BakerySettings settings = settingsService.current();
        return new RulesResponse(settings.getMinLeadTimeHours(), settings.getMaxBookingDaysAhead(), settings.getVersion());
    }

    @Transactional
    public RulesResponse updateRules(RulesRequest request, Long actorId) {
        BakerySettings settings = settingsService.current();
        Versions.require(settings.getVersion(), request.version());
        Changes changes = new Changes()
                .track("minLeadTimeHours", settings.getMinLeadTimeHours(), request.minLeadTimeHours())
                .track("maxBookingDaysAhead", settings.getMaxBookingDaysAhead(), request.maxBookingDaysAhead());
        settings.updateScheduling(request.minLeadTimeHours(), request.maxBookingDaysAhead());
        settingsService.flush();
        if (!changes.isEmpty()) {
            audit.record(ActorType.OWNER, actorId, "SCHEDULE_RULES_UPDATED", "bakery_settings",
                    BakerySettings.SINGLETON_ID, changes.asMap());
        }
        return new RulesResponse(settings.getMinLeadTimeHours(), settings.getMaxBookingDaysAhead(), settings.getVersion());
    }

    private WeeklySlot findSlot(Long id) {
        return slots.findById(id).orElseThrow(() -> new NotFoundException("Horário não encontrado."));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), properties.timezone());
    }
}
