package br.com.confeitaria.modules.catalog.service;

import org.springframework.stereotype.Service;

import br.com.confeitaria.common.audit.AuditService;
import br.com.confeitaria.modules.catalog.entity.CakeFilling;
import br.com.confeitaria.modules.catalog.repository.CakeFillingRepository;

@Service
public class CakeFillingService extends CatalogOptionService<CakeFilling> {

    public CakeFillingService(CakeFillingRepository repository, AuditService audit) {
        super(repository, audit, new Kind("cake_filling", "FILLING", "Recheio não encontrado.",
                "Já existe um recheio com este nome."));
    }

    @Override
    protected CakeFilling newOption(String name, String description, int displayOrder) {
        return new CakeFilling(name, description, displayOrder);
    }
}
