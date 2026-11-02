package br.com.confeitaria.modules.catalog.service;

import org.springframework.stereotype.Service;

import br.com.confeitaria.common.audit.AuditService;
import br.com.confeitaria.modules.catalog.entity.CakeFlavor;
import br.com.confeitaria.modules.catalog.repository.CakeFlavorRepository;

@Service
public class CakeFlavorService extends CatalogOptionService<CakeFlavor> {

    public CakeFlavorService(CakeFlavorRepository repository, AuditService audit) {
        super(repository, audit, new Kind("cake_flavor", "FLAVOR", "Massa não encontrada.",
                "Já existe uma massa com este nome."));
    }

    @Override
    protected CakeFlavor newOption(String name, String description, int displayOrder) {
        return new CakeFlavor(name, description, displayOrder);
    }
}
