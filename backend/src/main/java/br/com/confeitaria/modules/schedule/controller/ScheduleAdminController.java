package br.com.confeitaria.modules.schedule.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.BlockRequest;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.BlockResponse;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.CalendarDay;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.RulesRequest;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.RulesResponse;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.SlotRequest;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.SlotResponse;
import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.SlotUpdateRequest;
import br.com.confeitaria.modules.schedule.service.AvailabilityService;
import br.com.confeitaria.modules.schedule.service.ScheduleService;
import br.com.confeitaria.security.AdminPrincipal;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/schedule")
public class ScheduleAdminController {

    private final ScheduleService schedule;
    private final AvailabilityService availability;

    public ScheduleAdminController(ScheduleService schedule, AvailabilityService availability) {
        this.schedule = schedule;
        this.availability = availability;
    }

    @GetMapping("/rules")
    public RulesResponse rules() {
        return schedule.getRules();
    }

    @PutMapping("/rules")
    public RulesResponse updateRules(@Valid @RequestBody RulesRequest body, @AuthenticationPrincipal AdminPrincipal admin) {
        return schedule.updateRules(body, admin.getId());
    }

    @GetMapping("/slots")
    public List<SlotResponse> slots() {
        return schedule.listSlots();
    }

    @PostMapping("/slots")
    @ResponseStatus(HttpStatus.CREATED)
    public SlotResponse createSlot(@Valid @RequestBody SlotRequest body, @AuthenticationPrincipal AdminPrincipal admin) {
        return schedule.createSlot(body, admin.getId());
    }

    @PutMapping("/slots/{id}")
    public SlotResponse updateSlot(@PathVariable Long id, @Valid @RequestBody SlotUpdateRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return schedule.updateSlot(id, body, admin.getId());
    }

    @DeleteMapping("/slots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlot(@PathVariable Long id, @AuthenticationPrincipal AdminPrincipal admin) {
        schedule.deleteSlot(id, admin.getId());
    }

    @GetMapping("/blocks")
    public List<BlockResponse> blocks() {
        return schedule.listUpcomingBlocks();
    }

    @PostMapping("/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public BlockResponse createBlock(@Valid @RequestBody BlockRequest body, @AuthenticationPrincipal AdminPrincipal admin) {
        return schedule.createBlock(body, admin.getId());
    }

    @DeleteMapping("/blocks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBlock(@PathVariable Long id, @AuthenticationPrincipal AdminPrincipal admin) {
        schedule.deleteBlock(id, admin.getId());
    }

    @GetMapping("/calendar")
    public List<CalendarDay> calendar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return availability.calendar(from, to);
    }
}
