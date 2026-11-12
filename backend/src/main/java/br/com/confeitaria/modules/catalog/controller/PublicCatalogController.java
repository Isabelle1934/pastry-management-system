package br.com.confeitaria.modules.catalog.controller;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.modules.catalog.dto.CatalogDtos.PublicCatalog;
import br.com.confeitaria.modules.catalog.service.PublicCatalogService;
import br.com.confeitaria.modules.file.FileResponses;
import br.com.confeitaria.modules.file.service.FileStorageService;

@RestController
public class PublicCatalogController {

    private final PublicCatalogService service;
    private final FileStorageService storage;

    public PublicCatalogController(PublicCatalogService service, FileStorageService storage) {
        this.service = service;
        this.storage = storage;
    }

    @GetMapping("/api/public/catalog")
    public PublicCatalog catalog() {
        return service.activeCatalog();
    }

    /** Foto de produto visível no catálogo público. Produtos inativos não expõem fotos. */
    @GetMapping("/api/public/products/{productId}/photos/{fileId}")
    public ResponseEntity<InputStreamResource> photo(@PathVariable Long productId, @PathVariable Long fileId) {
        return FileResponses.publicImage(storage, service.publicPhoto(productId, fileId));
    }
}
