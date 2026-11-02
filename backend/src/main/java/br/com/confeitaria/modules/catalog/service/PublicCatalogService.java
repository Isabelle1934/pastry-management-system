package br.com.confeitaria.modules.catalog.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.modules.catalog.dto.CatalogDtos.PublicCatalog;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.PublicOption;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.PublicSize;
import br.com.confeitaria.modules.catalog.repository.CakeFillingRepository;
import br.com.confeitaria.modules.catalog.repository.CakeFlavorRepository;
import br.com.confeitaria.modules.catalog.repository.CakeSizeRepository;

/** Catálogo exibido no formulário de encomenda: somente itens ativos. */
@Service
public class PublicCatalogService {

    private final CakeSizeRepository sizes;
    private final CakeFlavorRepository flavors;
    private final CakeFillingRepository fillings;

    public PublicCatalogService(CakeSizeRepository sizes, CakeFlavorRepository flavors, CakeFillingRepository fillings) {
        this.sizes = sizes;
        this.flavors = flavors;
        this.fillings = fillings;
    }

    @Transactional(readOnly = true)
    public PublicCatalog activeCatalog() {
        return new PublicCatalog(
                sizes.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream().map(PublicSize::from).toList(),
                flavors.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream().map(PublicOption::from).toList(),
                fillings.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream().map(PublicOption::from).toList());
    }
}
