package br.com.confeitaria.modules.catalog.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.modules.catalog.dto.CatalogDtos.PublicCatalog;
import br.com.confeitaria.modules.catalog.service.PublicCatalogService;

@RestController
public class PublicCatalogController {

    private final PublicCatalogService service;

    public PublicCatalogController(PublicCatalogService service) {
        this.service = service;
    }

    @GetMapping("/api/public/catalog")
    public PublicCatalog catalog() {
        return service.activeCatalog();
    }
}
