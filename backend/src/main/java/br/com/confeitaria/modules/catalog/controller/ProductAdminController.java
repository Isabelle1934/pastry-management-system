package br.com.confeitaria.modules.catalog.controller;

import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.confeitaria.modules.catalog.dto.CatalogDtos.ActiveRequest;
import br.com.confeitaria.modules.catalog.dto.ProductDtos.CategoryRequest;
import br.com.confeitaria.modules.catalog.dto.ProductDtos.CategoryResponse;
import br.com.confeitaria.modules.catalog.dto.ProductDtos.ProductRequest;
import br.com.confeitaria.modules.catalog.dto.ProductDtos.ProductResponse;
import br.com.confeitaria.modules.catalog.dto.ProductDtos.StatusRequest;
import br.com.confeitaria.modules.catalog.service.ProductCategoryService;
import br.com.confeitaria.modules.catalog.service.ProductService;
import br.com.confeitaria.modules.file.FileResponses;
import br.com.confeitaria.modules.file.service.FileStorageService;
import br.com.confeitaria.security.AdminPrincipal;
import jakarta.validation.Valid;

/** Catálogo de produtos e categorias, administrado pela proprietária. */
@RestController
@RequestMapping("/api/admin/catalog")
public class ProductAdminController {

    private final ProductCategoryService categories;
    private final ProductService products;
    private final FileStorageService storage;

    public ProductAdminController(ProductCategoryService categories, ProductService products,
            FileStorageService storage) {
        this.categories = categories;
        this.products = products;
        this.storage = storage;
    }

    // --- Categorias ---

    @GetMapping("/categories")
    public List<CategoryResponse> listCategories() {
        return categories.list();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return categories.create(body, admin.getId());
    }

    @PutMapping("/categories/{id}")
    public CategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return categories.update(id, body, admin.getId());
    }

    @PatchMapping("/categories/{id}/active")
    public CategoryResponse setCategoryActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return categories.setActive(id, body.active(), body.version(), admin.getId());
    }

    // --- Produtos ---

    @GetMapping("/products")
    public List<ProductResponse> listProducts() {
        return products.list();
    }

    @GetMapping("/products/{id}")
    public ProductResponse product(@PathVariable Long id) {
        return products.get(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return products.create(body, admin.getId());
    }

    @PutMapping("/products/{id}")
    public ProductResponse updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return products.update(id, body, admin.getId());
    }

    @PatchMapping("/products/{id}/status")
    public ProductResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return products.changeStatus(id, body.status(), body.version(), admin.getId());
    }

    @PostMapping(path = "/products/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse addPhoto(@PathVariable Long id, @RequestPart("photo") MultipartFile photo,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return products.addPhoto(id, photo, admin.getId());
    }

    @DeleteMapping("/products/{id}/photos/{fileId}")
    public ProductResponse removePhoto(@PathVariable Long id, @PathVariable Long fileId,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return products.removePhoto(id, fileId, admin.getId());
    }

    @GetMapping("/products/{id}/photos/{fileId}")
    public ResponseEntity<InputStreamResource> photo(@PathVariable Long id, @PathVariable Long fileId) {
        return FileResponses.download(storage, products.photoForOwner(id, fileId));
    }
}
