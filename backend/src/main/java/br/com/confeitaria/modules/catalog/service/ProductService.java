package br.com.confeitaria.modules.catalog.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.audit.AuditService;
import br.com.confeitaria.common.audit.Changes;
import br.com.confeitaria.common.exception.ConflictException;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.common.exception.NotFoundException;
import br.com.confeitaria.common.persistence.Versions;
import br.com.confeitaria.modules.catalog.dto.ProductDtos.ProductRequest;
import br.com.confeitaria.modules.catalog.dto.ProductDtos.ProductResponse;
import br.com.confeitaria.modules.catalog.entity.Product;
import br.com.confeitaria.modules.catalog.entity.ProductCategory;
import br.com.confeitaria.modules.catalog.entity.ProductStatus;
import br.com.confeitaria.modules.catalog.repository.ProductRepository;
import br.com.confeitaria.modules.file.entity.FilePurpose;
import br.com.confeitaria.modules.file.entity.StoredFile;
import br.com.confeitaria.modules.file.repository.StoredFileRepository;
import br.com.confeitaria.modules.file.service.FileStorageService;

/**
 * Produtos do catálogo. Nunca são apagados (podem estar em pedidos): mudam de situação. Editar um produto
 * não altera pedidos já registrados, que guardam cópia de nome, categoria e preço.
 */
@Service
public class ProductService {

    static final String ENTITY = "product";
    public static final int MAX_PHOTOS = 6;

    private final ProductRepository repository;
    private final ProductCategoryService categories;
    private final FileStorageService storage;
    private final StoredFileRepository files;
    private final AuditService audit;
    private final Clock clock;

    public ProductService(ProductRepository repository, ProductCategoryService categories, FileStorageService storage,
            StoredFileRepository files, AuditService audit, Clock clock) {
        this.repository = repository;
        this.categories = categories;
        this.storage = storage;
        this.files = files;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        List<Product> all = repository.findAllForListing();
        Map<Long, List<Long>> photos = photoIdsByProduct(all.stream().map(Product::getId).toList());
        return all.stream().map(p -> ProductResponse.from(p, photos.getOrDefault(p.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return response(find(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request, Long actorId) {
        String name = request.name().strip();
        if (repository.existsByNameIgnoreCase(name)) {
            throw duplicateName();
        }
        Product product = repository.save(new Product(toData(request)));
        audit.record(ActorType.OWNER, actorId, "PRODUCT_CREATED", ENTITY, product.getId(), null);
        return response(product);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request, Long actorId) {
        Product product = find(id);
        if (request.version() == null) {
            throw new FieldValidationException("version", "Versão obrigatória.");
        }
        Versions.require(product.getVersion(), request.version());
        String name = request.name().strip();
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicateName();
        }
        Product.Data data = toData(request);
        Changes changes = new Changes()
                .track("name", product.getName(), name)
                .track("category", product.getCategory().getName(), data.category().getName())
                .track("price", product.getPrice(), data.price())
                .track("minQuantity", product.getMinQuantity(), data.minQuantity())
                .track("orderable", product.isOrderable(), data.orderable())
                .track("seasonal", product.isSeasonal(), data.seasonal())
                .track("availableFrom", String.valueOf(product.getAvailableFrom()), String.valueOf(data.availableFrom()))
                .track("availableUntil", String.valueOf(product.getAvailableUntil()),
                        String.valueOf(data.availableUntil()))
                .track("availableDays", product.getAvailableDays().toString(), normalizedDays(data).toString())
                .track("pickupAllowed", product.isPickupAllowed(), data.pickupAllowed())
                .track("deliveryAllowed", product.isDeliveryAllowed(), data.deliveryAllowed());
        product.update(data);
        repository.flush();
        if (!changes.isEmpty()) {
            audit.record(ActorType.OWNER, actorId, "PRODUCT_UPDATED", ENTITY, id, changes.asMap());
        }
        return response(product);
    }

    /** Ativar, deixar temporariamente indisponível ou tirar do catálogo público (sem apagar nada). */
    @Transactional
    public ProductResponse changeStatus(Long id, ProductStatus status, long version, Long actorId) {
        Product product = find(id);
        Versions.require(product.getVersion(), version);
        if (product.getStatus() != status) {
            ProductStatus previous = product.getStatus();
            product.changeStatus(status);
            repository.flush();
            audit.record(ActorType.OWNER, actorId, "PRODUCT_STATUS_CHANGED", ENTITY, id,
                    Map.of("status", Map.of("from", previous.name(), "to", status.name())));
        }
        return response(product);
    }

    @Transactional
    public ProductResponse addPhoto(Long id, MultipartFile file, Long actorId) {
        Product product = find(id);
        if (files.findByProductIdAndDeletedAtIsNullOrderByIdAsc(id).size() >= MAX_PHOTOS) {
            throw new FieldValidationException("photo", "Cada produto pode ter no máximo " + MAX_PHOTOS + " fotos.");
        }
        var validated = storage.validate(file, FilePurpose.PRODUCT_PHOTO, "photo");
        StoredFile stored = storage.store(validated, id, FilePurpose.PRODUCT_PHOTO, ActorType.OWNER);
        audit.record(ActorType.OWNER, actorId, "PRODUCT_PHOTO_ADDED", ENTITY, id, Map.of("fileId", stored.getId()));
        return response(product);
    }

    /** Remove a foto do catálogo (exclusão lógica: o registro e o arquivo são mantidos). */
    @Transactional
    public ProductResponse removePhoto(Long id, Long fileId, Long actorId) {
        Product product = find(id);
        StoredFile photo = storage.findForProduct(id, fileId);
        photo.markDeleted(clock.instant());
        files.flush();
        audit.record(ActorType.OWNER, actorId, "PRODUCT_PHOTO_REMOVED", ENTITY, id, Map.of("fileId", fileId));
        return response(product);
    }

    @Transactional(readOnly = true)
    public StoredFile photoForOwner(Long id, Long fileId) {
        find(id);
        return storage.findForProduct(id, fileId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Long>> photoIdsByProduct(List<Long> productIds) {
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        files.findByProductIdInAndDeletedAtIsNullOrderByIdAsc(productIds)
                .forEach(f -> result.computeIfAbsent(f.getProductId(), k -> new java.util.ArrayList<>()).add(f.getId()));
        return result;
    }

    private ProductResponse response(Product product) {
        return ProductResponse.from(product, files.findByProductIdAndDeletedAtIsNullOrderByIdAsc(product.getId())
                .stream().map(StoredFile::getId).toList());
    }

    private Product.Data toData(ProductRequest r) {
        ProductCategory category = categories.find(r.categoryId());
        if (r.availableFrom() != null && r.availableUntil() != null && r.availableUntil().isBefore(r.availableFrom())) {
            throw new FieldValidationException("availableUntil", "A data final precisa ser igual ou depois da inicial.");
        }
        boolean pickup = r.pickupAllowed() == null || r.pickupAllowed();
        boolean delivery = r.deliveryAllowed() != null && r.deliveryAllowed();
        if (!pickup && !delivery) {
            throw new FieldValidationException("pickupAllowed", "Escolha pelo menos uma forma: retirada ou delivery.");
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (r.availableDays() != null) {
            r.availableDays().forEach(d -> days.add(DayOfWeek.of(d)));
        }
        return new Product.Data(category, r.name(), r.description(), r.price(), r.unitLabel(),
                r.minQuantity() == null ? 1 : r.minQuantity(), r.orderable() == null || r.orderable(),
                r.seasonal() != null && r.seasonal(), r.availableFrom(), r.availableUntil(), days, pickup, delivery,
                r.displayOrder() == null ? 0 : r.displayOrder());
    }

    private static Set<DayOfWeek> normalizedDays(Product.Data data) {
        return data.availableDays().size() == 7 ? Set.of() : data.availableDays();
    }

    Product find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Produto não encontrado."));
    }

    private static ConflictException duplicateName() {
        return new ConflictException("DUPLICATE_NAME", "Já existe um produto com este nome.");
    }
}
