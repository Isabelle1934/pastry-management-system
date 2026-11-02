package br.com.confeitaria.modules.finance.service;

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
import br.com.confeitaria.modules.finance.dto.FinanceDtos.CategoryRequest;
import br.com.confeitaria.modules.finance.dto.FinanceDtos.CategoryResponse;
import br.com.confeitaria.modules.finance.entity.ExpenseCategory;
import br.com.confeitaria.modules.finance.repository.ExpenseCategoryRepository;

/** Categorias de despesa. Não são apagadas: são desativadas (somem do formulário, ficam nas despesas antigas). */
@Service
public class ExpenseCategoryService {

    private static final String ENTITY = "expense_category";

    private final ExpenseCategoryRepository repository;
    private final AuditService audit;

    public ExpenseCategoryService(ExpenseCategoryRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request, Long actorId) {
        String name = request.name().strip();
        if (repository.existsByNameIgnoreCase(name)) {
            throw duplicateName();
        }
        ExpenseCategory category = repository.save(new ExpenseCategory(name));
        audit.record(ActorType.OWNER, actorId, "EXPENSE_CATEGORY_CREATED", ENTITY, category.getId(), null);
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse rename(Long id, CategoryRequest request, Long actorId) {
        ExpenseCategory category = find(id);
        if (request.version() == null) {
            throw new FieldValidationException("version", "Versão obrigatória.");
        }
        Versions.require(category.getVersion(), request.version());
        String name = request.name().strip();
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicateName();
        }
        Changes changes = new Changes().track("name", category.getName(), name);
        category.rename(name);
        repository.flush();
        if (!changes.isEmpty()) {
            audit.record(ActorType.OWNER, actorId, "EXPENSE_CATEGORY_UPDATED", ENTITY, id, changes.asMap());
        }
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse setActive(Long id, boolean active, long version, Long actorId) {
        ExpenseCategory category = find(id);
        Versions.require(category.getVersion(), version);
        if (category.isActive() != active) {
            category.setActive(active);
            repository.flush();
            audit.record(ActorType.OWNER, actorId,
                    active ? "EXPENSE_CATEGORY_ACTIVATED" : "EXPENSE_CATEGORY_DEACTIVATED", ENTITY, id, null);
        }
        return CategoryResponse.from(category);
    }

    ExpenseCategory find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Categoria não encontrada."));
    }

    private static ConflictException duplicateName() {
        return new ConflictException("DUPLICATE_NAME", "Já existe uma categoria com este nome.");
    }
}
