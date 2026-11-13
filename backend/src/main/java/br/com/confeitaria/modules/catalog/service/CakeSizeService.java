package br.com.confeitaria.modules.catalog.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.audit.AuditService;
import br.com.confeitaria.common.audit.Changes;
import br.com.confeitaria.common.exception.ConflictException;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.common.exception.NotFoundException;
import br.com.confeitaria.common.persistence.Versions;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.SizeRequest;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.SizeResponse;
import br.com.confeitaria.modules.catalog.entity.CakeSize;
import br.com.confeitaria.modules.catalog.repository.CakeSizeRepository;
import br.com.confeitaria.modules.catalog.entity.CakeCategory;
import br.com.confeitaria.modules.catalog.repository.CakeCategoryRepository;

@Service
public class CakeSizeService {

    static final String ENTITY = "cake_size";

    private final CakeSizeRepository repository;
    private final CakeCategoryRepository categories;
    private final AuditService audit;

    public CakeSizeService(CakeSizeRepository repository, CakeCategoryRepository categories, AuditService audit) {
        this.repository = repository;
        this.categories = categories;
        this.audit = audit;
    }

    /** Categoria escolhida no cadastro do tamanho (pode estar inativa: o tamanho só não aparece no site). */
    private CakeCategory categoryOf(SizeRequest request) {
        if (request.categoryId() == null) {
            return null;
        }
        CakeCategory category = categories.findById(request.categoryId())
                .orElseThrow(() -> new FieldValidationException("categoryId", "Categoria não encontrada."));
        if (category.isConsultOnly()) {
            throw new FieldValidationException("categoryId",
                    "Esta categoria é \"consulte valores\" e não tem tabela de tamanhos.");
        }
        return category;
    }

    @Transactional(readOnly = true)
    public List<SizeResponse> list() {
        return repository.findAllByOrderByDisplayOrderAscNameAsc().stream().map(SizeResponse::from).toList();
    }

    @Transactional
    public SizeResponse create(SizeRequest request, Long actorId) {
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw duplicateName();
        }
        CakeCategory category = categoryOf(request);
        CakeSize size = new CakeSize(name, request.description(), request.servings(),
                request.basePrice(), request.maxFlavors(), request.maxFillings(), orderOf(request));
        size.classify(category, request.weightKg(), request.diameterCm());
        size = repository.save(size);
        audit.record(ActorType.OWNER, actorId, "SIZE_CREATED", ENTITY, size.getId(), null);
        return SizeResponse.from(size);
    }

    @Transactional
    public SizeResponse update(Long id, SizeRequest request, Long actorId) {
        CakeSize size = find(id);
        if (request.version() == null) {
            throw new FieldValidationException("version", "Versão obrigatória.");
        }
        Versions.require(size.getVersion(), request.version());
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicateName();
        }

        CakeCategory category = categoryOf(request);
        Changes changes = new Changes()
                .track("name", size.getName(), name)
                .track("category", size.getCategory() == null ? null : size.getCategory().getName(),
                        category == null ? null : category.getName())
                .track("weightKg", size.getWeightKg(), request.weightKg())
                .track("diameterCm", size.getDiameterCm(), request.diameterCm())
                .track("basePrice", size.getBasePrice(), request.basePrice())
                .track("maxFlavors", size.getMaxFlavors(), request.maxFlavors())
                .track("maxFillings", size.getMaxFillings(), request.maxFillings())
                .track("servings", size.getServings(), request.servings())
                .track("displayOrder", size.getDisplayOrder(), orderOf(request));
        size.update(name, request.description(), request.servings(), request.basePrice(), request.maxFlavors(),
                request.maxFillings(), orderOf(request));
        size.classify(category, request.weightKg(), request.diameterCm());
        repository.flush();
        if (!changes.isEmpty()) {
            audit.record(ActorType.OWNER, actorId, "SIZE_UPDATED", ENTITY, id, changes.asMap());
        }
        return SizeResponse.from(size);
    }

    /** Tamanhos não são apagados (podem estar em pedidos): são desativados e somem do formulário público. */
    @Transactional
    public SizeResponse setActive(Long id, boolean active, long version, Long actorId) {
        CakeSize size = find(id);
        Versions.require(size.getVersion(), version);
        if (size.isActive() != active) {
            size.setActive(active);
            repository.flush();
            audit.record(ActorType.OWNER, actorId, active ? "SIZE_ACTIVATED" : "SIZE_DEACTIVATED", ENTITY, id, null);
        }
        return SizeResponse.from(size);
    }

    private CakeSize find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Tamanho não encontrado."));
    }

    private static int orderOf(SizeRequest request) {
        return request.displayOrder() == null ? 0 : request.displayOrder();
    }

    private static ConflictException duplicateName() {
        return new ConflictException("DUPLICATE_NAME", "Já existe um tamanho com este nome.");
    }
}
