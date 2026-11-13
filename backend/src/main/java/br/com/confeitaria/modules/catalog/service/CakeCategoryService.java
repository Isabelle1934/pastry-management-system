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
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.CakeCategoryRequest;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.CakeCategoryResponse;
import br.com.confeitaria.modules.catalog.entity.CakeCategory;
import br.com.confeitaria.modules.catalog.repository.CakeCategoryRepository;

/**
 * Categorias de bolo personalizado. Não são apagadas (pedidos guardam a categoria): desativar esconde a
 * categoria e seus tamanhos do site.
 */
@Service
public class CakeCategoryService {

    static final String ENTITY = "cake_category";

    private final CakeCategoryRepository repository;
    private final AuditService audit;

    public CakeCategoryService(CakeCategoryRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CakeCategoryResponse> list() {
        Map<Long, Long> counts = counts();
        return repository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .map(c -> CakeCategoryResponse.from(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CakeCategoryResponse create(CakeCategoryRequest request, Long actorId) {
        String name = request.name().strip();
        if (repository.existsByNameIgnoreCase(name)) {
            throw duplicateName();
        }
        CakeCategory category = repository.save(new CakeCategory(name, request.description(), request.notice(),
                Boolean.TRUE.equals(request.consultOnly()), maxFlavors(request), maxFillings(request), orderOf(request)));
        audit.record(ActorType.OWNER, actorId, "CAKE_CATEGORY_CREATED", ENTITY, category.getId(), null);
        return CakeCategoryResponse.from(category, 0);
    }

    @Transactional
    public CakeCategoryResponse update(Long id, CakeCategoryRequest request, Long actorId) {
        CakeCategory category = find(id);
        if (request.version() == null) {
            throw new FieldValidationException("version", "Versão obrigatória.");
        }
        Versions.require(category.getVersion(), request.version());
        String name = request.name().strip();
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicateName();
        }
        boolean consultOnly = Boolean.TRUE.equals(request.consultOnly());
        long sizes = counts().getOrDefault(id, 0L);
        if (consultOnly && sizes > 0) {
            throw new FieldValidationException("consultOnly",
                    "Esta categoria tem tamanhos cadastrados. Mova os tamanhos para outra categoria antes.");
        }
        Changes changes = new Changes()
                .track("name", category.getName(), name)
                .track("description", category.getDescription(), request.description())
                .track("notice", category.getNotice(), request.notice())
                .track("consultOnly", category.isConsultOnly(), consultOnly)
                .track("maxFlavors", category.getMaxFlavors(), maxFlavors(request))
                .track("maxFillings", category.getMaxFillings(), maxFillings(request))
                .track("displayOrder", category.getDisplayOrder(), orderOf(request));
        category.update(name, request.description(), request.notice(), consultOnly, maxFlavors(request),
                maxFillings(request), orderOf(request));
        repository.flush();
        if (!changes.isEmpty()) {
            audit.record(ActorType.OWNER, actorId, "CAKE_CATEGORY_UPDATED", ENTITY, id, changes.asMap());
        }
        return CakeCategoryResponse.from(category, sizes);
    }

    @Transactional
    public CakeCategoryResponse setActive(Long id, boolean active, long version, Long actorId) {
        CakeCategory category = find(id);
        Versions.require(category.getVersion(), version);
        if (category.isActive() != active) {
            category.setActive(active);
            repository.flush();
            audit.record(ActorType.OWNER, actorId, active ? "CAKE_CATEGORY_ACTIVATED" : "CAKE_CATEGORY_DEACTIVATED",
                    ENTITY, id, null);
        }
        return CakeCategoryResponse.from(category, counts().getOrDefault(id, 0L));
    }

    private CakeCategory find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Categoria não encontrada."));
    }

    private Map<Long, Long> counts() {
        Map<Long, Long> counts = new HashMap<>();
        repository.countSizes().forEach(c -> counts.put(c.getCategoryId(), c.getTotal()));
        return counts;
    }

    private static int maxFlavors(CakeCategoryRequest request) {
        return request.maxFlavors() == null ? 1 : request.maxFlavors();
    }

    private static int maxFillings(CakeCategoryRequest request) {
        return request.maxFillings() == null ? 2 : request.maxFillings();
    }

    private static int orderOf(CakeCategoryRequest request) {
        return request.displayOrder() == null ? 0 : request.displayOrder();
    }

    private static ConflictException duplicateName() {
        return new ConflictException("DUPLICATE_NAME", "Já existe uma categoria de bolo com este nome.");
    }
}
