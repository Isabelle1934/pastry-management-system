package br.com.confeitaria.modules.schedule.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.modules.schedule.dto.ScheduleDtos.PublicAvailability;
import br.com.confeitaria.modules.schedule.service.AvailabilityService;

@RestController
public class PublicAvailabilityController {

    private final AvailabilityService availability;

    public PublicAvailabilityController(AvailabilityService availability) {
        this.availability = availability;
    }

    /** Sem parâmetros: a partir de hoje, até 62 dias ou o limite de agendamento configurado. */
    @GetMapping("/api/public/availability")
    public PublicAvailability availability(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return availability.publicAvailability(from, to);
    }
}
