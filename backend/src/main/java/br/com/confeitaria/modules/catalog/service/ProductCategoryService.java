package br.com.confeitaria.modules.catalog.service;

import java.util.HashMap;
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
import br.com.confeitaria.modules.catalog.dto.ProductDtos.CategoryRequest;
import br.com.confeitaria.modules.catalog.dto.ProductDtos.CategoryResponse;
import br.com.confeitaria.modules.catalog.entity.ProductCategory;
import br.com.confeitaria.modules.catalog.repository.ProductCategoryRepository;
import br.com.confeitaria.modules.catalog.repository.ProductRepository;

/** Categorias do catálogo. Não são apagadas: desativar esconde a categoria e seus produtos do site. */
@Service
public class ProductCategoryService {

    static final String ENTITY = "product_category";

    private final ProductCategoryRepository repository;
    private final ProductRepository products;
    private final AuditService audit;

    public ProductCategoryService(ProductCategoryRepository repository, ProductRepository products, AuditService audit) {
        this.repository = repository;
        this.products = products;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        Map<Long, Long> counts = new HashMap<>();
        products.countByCategory().forEach(c -> counts.put(c.getCategoryId(), c.getTotal()));
        return repository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .map(c -> CategoryResponse.from(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request, Long actorId) {
        String name = request.name().strip();
        if (repository.existsByNameIgnoreCase(name)) {
            throw duplicateName();
        }
        ProductCategory category = repository.save(new ProductCategory(name, request.description(), orderOf(request)));
        audit.record(ActorType.OWNER, actorId, "CATEGORY_CREATED", ENTITY, category.getId(), null);
        return CategoryResponse.from(category, 0);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request, Long actorId) {
        ProductCategory category = find(id);
        if (request.version() == null) {
            throw new FieldValidationException("version", "Versão obrigatória.");
        }
        Versions.require(category.getVersion(), request.version());
        String name = request.name().strip();
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicateName();
        }
        Changes changes = new Changes()
                .track("name", category.getName(), name)
                .track("displayOrder", category.getDisplayOrder(), orderOf(request));
        category.update(name, request.description(), orderOf(request));
        repository.flush();
        if (!changes.isEmpty()) {
            audit.record(ActorType.OWNER, actorId, "CATEGORY_UPDATED", ENTITY, id, changes.asMap());
        }
        return CategoryResponse.from(category, countOf(id));
    }

    @Transactional
    public CategoryResponse setActive(Long id, boolean active, long version, Long actorId) {
        ProductCategory category = find(id);
        Versions.require(category.getVersion(), version);
        if (category.isActive() != active) {
            category.setActive(active);
            repository.flush();
            audit.record(ActorType.OWNER, actorId, active ? "CATEGORY_ACTIVATED" : "CATEGORY_DEACTIVATED", ENTITY, id,
                    null);
        }
        return CategoryResponse.from(category, countOf(id));
    }

    ProductCategory find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Categoria não encontrada."));
    }

    private long countOf(Long categoryId) {
        return products.countByCategory().stream()
                .filter(c -> c.getCategoryId().equals(categoryId))
                .mapToLong(ProductRepository.CategoryCount::getTotal)
                .sum();
    }

    private static int orderOf(CategoryRequest request) {
        return request.displayOrder() == null ? 0 : request.displayOrder();
    }

    private static ConflictException duplicateName() {
        return new ConflictException("DUPLICATE_NAME", "Já existe uma categoria com este nome.");
    }
}
