package br.com.confeitaria.modules.catalog.service;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.audit.AuditService;
import br.com.confeitaria.common.audit.Changes;
import br.com.confeitaria.common.exception.ConflictException;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.common.exception.NotFoundException;
import br.com.confeitaria.common.persistence.Versions;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.OptionRequest;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.OptionResponse;
import br.com.confeitaria.modules.catalog.entity.CatalogOption;
import br.com.confeitaria.modules.catalog.repository.CatalogOptionRepository;

/**
 * Regras comuns a massas e recheios. Itens não são apagados: são desativados,
 * pois podem estar vinculados a pedidos.
 */
public abstract class CatalogOptionService<T extends CatalogOption> {

    /**
     * @param entity           nome da tabela, usado na auditoria
     * @param action           prefixo da ação de auditoria (FLAVOR, FILLING)
     * @param notFoundMessage  mensagem quando o item não existe
     * @param duplicateMessage mensagem quando o nome já está em uso
     */
    protected record Kind(String entity, String action, String notFoundMessage, String duplicateMessage) {
    }

    private final CatalogOptionRepository<T> repository;
    private final AuditService audit;
    private final Kind kind;

    protected CatalogOptionService(CatalogOptionRepository<T> repository, AuditService audit, Kind kind) {
        this.repository = repository;
        this.audit = audit;
        this.kind = kind;
    }

    protected abstract T newOption(String name, String description, int displayOrder);

    @Transactional(readOnly = true)
    public List<OptionResponse> list() {
        return repository.findAllByOrderByDisplayOrderAscNameAsc().stream().map(OptionResponse::from).toList();
    }

    @Transactional
    public OptionResponse create(OptionRequest request, Long actorId) {
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw duplicateName();
        }
        T option = repository.save(newOption(name, request.description(), orderOf(request)));
        audit.record(ActorType.OWNER, actorId, kind.action() + "_CREATED", kind.entity(), option.getId(), null);
        return OptionResponse.from(option);
    }

    @Transactional
    public OptionResponse update(Long id, OptionRequest request, Long actorId) {
        T option = find(id);
        if (request.version() == null) {
            throw new FieldValidationException("version", "Versão obrigatória.");
        }
        Versions.require(option.getVersion(), request.version());
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicateName();
        }
        Changes changes = new Changes()
                .track("name", option.getName(), name)
                .track("displayOrder", option.getDisplayOrder(), orderOf(request));
        option.update(name, request.description(), orderOf(request));
        repository.flush();
        if (!changes.isEmpty()) {
            audit.record(ActorType.OWNER, actorId, kind.action() + "_UPDATED", kind.entity(), id, changes.asMap());
        }
        return OptionResponse.from(option);
    }

    @Transactional
    public OptionResponse setActive(Long id, boolean active, long version, Long actorId) {
        T option = find(id);
        Versions.require(option.getVersion(), version);
        if (option.isActive() != active) {
            option.setActive(active);
            repository.flush();
            audit.record(ActorType.OWNER, actorId, kind.action() + (active ? "_ACTIVATED" : "_DEACTIVATED"),
                    kind.entity(), id, null);
        }
        return OptionResponse.from(option);
    }

    private T find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException(kind.notFoundMessage()));
    }

    private static int orderOf(OptionRequest request) {
        return request.displayOrder() == null ? 0 : request.displayOrder();
    }

    private ConflictException duplicateName() {
        return new ConflictException("DUPLICATE_NAME", kind.duplicateMessage());
    }
}
