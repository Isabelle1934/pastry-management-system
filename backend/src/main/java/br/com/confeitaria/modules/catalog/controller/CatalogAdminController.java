package br.com.confeitaria.modules.catalog.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.modules.catalog.dto.CatalogDtos.ActiveRequest;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.CakeCategoryRequest;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.CakeCategoryResponse;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.OptionRequest;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.OptionResponse;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.SizeRequest;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.SizeResponse;
import br.com.confeitaria.modules.catalog.service.CakeCategoryService;
import br.com.confeitaria.modules.catalog.service.CakeFillingService;
import br.com.confeitaria.modules.catalog.service.CakeFlavorService;
import br.com.confeitaria.modules.catalog.service.CakeSizeService;
import br.com.confeitaria.modules.catalog.service.CatalogOptionService;
import br.com.confeitaria.security.AdminPrincipal;
import jakarta.validation.Valid;

/** Cadastro de categorias de bolo, tamanhos, massas e recheios pela proprietária. */
@RestController
@RequestMapping("/api/admin/catalog")
public class CatalogAdminController {

    private final CakeSizeService sizes;
    private final CakeCategoryService cakeCategories;
    private final CakeFlavorService flavors;
    private final CakeFillingService fillings;

    public CatalogAdminController(CakeSizeService sizes, CakeCategoryService cakeCategories, CakeFlavorService flavors,
            CakeFillingService fillings) {
        this.sizes = sizes;
        this.cakeCategories = cakeCategories;
        this.flavors = flavors;
        this.fillings = fillings;
    }

    // --- Categorias de bolo ---

    @GetMapping("/cake-categories")
    public List<CakeCategoryResponse> listCakeCategories() {
        return cakeCategories.list();
    }

    @PostMapping("/cake-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CakeCategoryResponse createCakeCategory(@Valid @RequestBody CakeCategoryRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return cakeCategories.create(body, admin.getId());
    }

    @PutMapping("/cake-categories/{id}")
    public CakeCategoryResponse updateCakeCategory(@PathVariable Long id, @Valid @RequestBody CakeCategoryRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return cakeCategories.update(id, body, admin.getId());
    }

    @PatchMapping("/cake-categories/{id}/active")
    public CakeCategoryResponse setCakeCategoryActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return cakeCategories.setActive(id, body.active(), body.version(), admin.getId());
    }

    // --- Tamanhos ---

    @GetMapping("/sizes")
    public List<SizeResponse> listSizes() {
        return sizes.list();
    }

    @PostMapping("/sizes")
    @ResponseStatus(HttpStatus.CREATED)
    public SizeResponse createSize(@Valid @RequestBody SizeRequest body, @AuthenticationPrincipal AdminPrincipal admin) {
        return sizes.create(body, admin.getId());
    }

    @PutMapping("/sizes/{id}")
    public SizeResponse updateSize(@PathVariable Long id, @Valid @RequestBody SizeRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return sizes.update(id, body, admin.getId());
    }

    @PatchMapping("/sizes/{id}/active")
    public SizeResponse setSizeActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return sizes.setActive(id, body.active(), body.version(), admin.getId());
    }

    // --- Massas ---

    @GetMapping("/flavors")
    public List<OptionResponse> listFlavors() {
        return flavors.list();
    }

    @PostMapping("/flavors")
    @ResponseStatus(HttpStatus.CREATED)
    public OptionResponse createFlavor(@Valid @RequestBody OptionRequest body, @AuthenticationPrincipal AdminPrincipal admin) {
        return flavors.create(body, admin.getId());
    }

    @PutMapping("/flavors/{id}")
    public OptionResponse updateFlavor(@PathVariable Long id, @Valid @RequestBody OptionRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return flavors.update(id, body, admin.getId());
    }

    @PatchMapping("/flavors/{id}/active")
    public OptionResponse setFlavorActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return setActive(flavors, id, body, admin);
    }

    // --- Recheios ---

    @GetMapping("/fillings")
    public List<OptionResponse> listFillings() {
        return fillings.list();
    }

    @PostMapping("/fillings")
    @ResponseStatus(HttpStatus.CREATED)
    public OptionResponse createFilling(@Valid @RequestBody OptionRequest body, @AuthenticationPrincipal AdminPrincipal admin) {
        return fillings.create(body, admin.getId());
    }

    @PutMapping("/fillings/{id}")
    public OptionResponse updateFilling(@PathVariable Long id, @Valid @RequestBody OptionRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return fillings.update(id, body, admin.getId());
    }

    @PatchMapping("/fillings/{id}/active")
    public OptionResponse setFillingActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return setActive(fillings, id, body, admin);
    }

    private static OptionResponse setActive(CatalogOptionService<?> service, Long id, ActiveRequest body,
            AdminPrincipal admin) {
        return service.setActive(id, body.active(), body.version(), admin.getId());
    }
}
