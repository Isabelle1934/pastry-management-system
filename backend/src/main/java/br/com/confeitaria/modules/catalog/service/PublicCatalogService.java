package br.com.confeitaria.modules.catalog.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.common.exception.NotFoundException;
import br.com.confeitaria.config.AppProperties;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.PublicCatalog;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.PublicOption;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.PublicSize;
import br.com.confeitaria.modules.catalog.dto.ProductDtos.PublicCategory;
import br.com.confeitaria.modules.catalog.dto.ProductDtos.PublicProduct;
import br.com.confeitaria.modules.catalog.entity.Product;
import br.com.confeitaria.modules.catalog.entity.ProductStatus;
import br.com.confeitaria.modules.catalog.repository.CakeFillingRepository;
import br.com.confeitaria.modules.catalog.repository.CakeFlavorRepository;
import br.com.confeitaria.modules.catalog.repository.CakeSizeRepository;
import br.com.confeitaria.modules.catalog.repository.ProductCategoryRepository;
import br.com.confeitaria.modules.catalog.repository.ProductRepository;
import br.com.confeitaria.modules.file.entity.StoredFile;
import br.com.confeitaria.modules.file.service.FileStorageService;
import br.com.confeitaria.modules.settings.service.SettingsService;
import br.com.confeitaria.modules.catalog.dto.CatalogDtos.PublicCakeCategory;
import br.com.confeitaria.modules.catalog.entity.CakeSize;
import br.com.confeitaria.modules.catalog.repository.CakeCategoryRepository;

/**
 * Catálogo público: produtos ativos ou temporariamente indisponíveis (categorias ativas, período vigente)
 * e as opções do bolo personalizado. Reflete na hora o que a proprietária altera no painel.
 */
@Service
public class PublicCatalogService {

    private final CakeSizeRepository sizes;
    private final CakeCategoryRepository cakeCategories;
    private final CakeFlavorRepository flavors;
    private final CakeFillingRepository fillings;
    private final ProductCategoryRepository categories;
    private final ProductRepository products;
    private final ProductService productService;
    private final FileStorageService storage;
    private final SettingsService settings;
    private final Clock clock;
    private final AppProperties properties;

    public PublicCatalogService(CakeSizeRepository sizes, CakeCategoryRepository cakeCategories, CakeFlavorRepository flavors, CakeFillingRepository fillings,
            ProductCategoryRepository categories, ProductRepository products, ProductService productService,
            FileStorageService storage, SettingsService settings, Clock clock, AppProperties properties) {
        this.sizes = sizes;
        this.cakeCategories = cakeCategories;
        this.flavors = flavors;
        this.fillings = fillings;
        this.categories = categories;
        this.products = products;
        this.productService = productService;
        this.storage = storage;
        this.settings = settings;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PublicCatalog activeCatalog() {
        LocalDate today = today();
        List<Product> visible = products.findPublicCandidates(ProductStatus.INACTIVE).stream()
                .filter(p -> p.isPubliclyVisible(today))
                .toList();
        Map<Long, List<Long>> photos = productService.photoIdsByProduct(visible.stream().map(Product::getId).toList());
        List<Long> usedCategories = visible.stream().map(p -> p.getCategory().getId()).distinct().toList();
        return new PublicCatalog(
                sizes.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                        .filter(CakeSize::isPubliclyVisible).map(PublicSize::from).toList(),
                flavors.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream().map(PublicOption::from).toList(),
                fillings.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream().map(PublicOption::from).toList(),
                categories.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                        .filter(c -> usedCategories.contains(c.getId()))
                        .map(PublicCategory::from).toList(),
                visible.stream().map(p -> PublicProduct.from(p, photos.getOrDefault(p.getId(), List.of()))).toList(),
                settings.current().isCustomCakeEnabled(),
                cakeCategories.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                        .map(PublicCakeCategory::from).toList());
    }

    @Transactional(readOnly = true)
    public StoredFile publicPhoto(Long productId, Long fileId) {
        Product product = products.findById(productId)
                .filter(p -> p.isPubliclyVisible(today()))
                .orElseThrow(() -> new NotFoundException("Foto não encontrada."));
        return storage.findForProduct(product.getId(), fileId);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), properties.timezone());
    }
}
