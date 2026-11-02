package br.com.confeitaria.modules.settings.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.modules.settings.dto.SettingsDtos.PublicBakeryInfo;
import br.com.confeitaria.modules.settings.dto.SettingsDtos.SettingsRequest;
import br.com.confeitaria.modules.settings.dto.SettingsDtos.SettingsResponse;
import br.com.confeitaria.modules.settings.service.SettingsService;
import br.com.confeitaria.security.AdminPrincipal;
import jakarta.validation.Valid;

@RestController
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/settings")
    public SettingsResponse get() {
        return service.get();
    }

    @PutMapping("/api/admin/settings")
    public SettingsResponse update(@Valid @RequestBody SettingsRequest body, @AuthenticationPrincipal AdminPrincipal admin) {
        return service.update(body, admin.getId());
    }

    @GetMapping("/api/public/bakery")
    public PublicBakeryInfo publicInfo() {
        return service.publicInfo();
    }
}
