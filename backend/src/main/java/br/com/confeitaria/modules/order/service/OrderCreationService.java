package br.com.confeitaria.modules.order.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.common.validation.WhatsAppNumber;
import br.com.confeitaria.modules.catalog.entity.CakeSize;
import br.com.confeitaria.modules.catalog.entity.CatalogOption;
import br.com.confeitaria.modules.catalog.repository.CakeFillingRepository;
import br.com.confeitaria.modules.catalog.repository.CakeFlavorRepository;
import br.com.confeitaria.modules.catalog.repository.CakeSizeRepository;
import br.com.confeitaria.modules.file.entity.FilePurpose;
import br.com.confeitaria.modules.file.service.FileStorageService;
import br.com.confeitaria.modules.file.service.FileStorageService.ValidatedFile;
import br.com.confeitaria.modules.order.dto.PublicOrderDtos.OrderCreatedResponse;
import br.com.confeitaria.modules.order.dto.PublicOrderDtos.OrderRequest;
import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.order.repository.CakeOrderRepository;
import br.com.confeitaria.modules.schedule.service.AvailabilityService;

/**
 * Recebe a solicitação de encomenda do formulário público. Toda regra é revalidada aqui,
 * independentemente do que o navegador já validou. A solicitação NÃO ocupa vaga na agenda.
 */
@Service
public class OrderCreationService {

    static final int MAX_REFERENCE_PHOTOS = 3;
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final CakeOrderRepository orders;
    private final CakeSizeRepository sizes;
    private final CakeFlavorRepository flavors;
    private final CakeFillingRepository fillings;
    private final AvailabilityService availability;
    private final FileStorageService files;
    private final OrderHistoryService history;
    private final OrderRateLimiter rateLimiter;

    public OrderCreationService(CakeOrderRepository orders, CakeSizeRepository sizes, CakeFlavorRepository flavors,
            CakeFillingRepository fillings, AvailabilityService availability, FileStorageService files,
            OrderHistoryService history, OrderRateLimiter rateLimiter) {
        this.orders = orders;
        this.sizes = sizes;
        this.flavors = flavors;
        this.fillings = fillings;
        this.availability = availability;
        this.files = files;
        this.history = history;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public OrderCreatedResponse create(OrderRequest request, List<MultipartFile> referencePhotos,
            MultipartFile topperPhoto, String ip) {
        rateLimiter.acquire(ip);

        // Etapa 1
        String name = request.customerName().strip().replaceAll("\\s+", " ");
        String whatsapp = WhatsAppNumber.normalize(request.customerWhatsapp())
                .orElseThrow(() -> new FieldValidationException("customerWhatsapp",
                        "Informe um WhatsApp válido com DDD, ex.: (11) 99999-8888."));

        // Etapa 3 (antes da agenda: o tamanho escolhido precisa existir para validar as opções)
        CakeSize size = sizes.findById(request.sizeId()).filter(CakeSize::isActive)
                .orElseThrow(() -> new FieldValidationException("sizeId", "Este tamanho não está mais disponível."));
        Map<Long, String> chosenFlavors = resolveOptions(request.flavorIds(), size.getMaxFlavors(), "flavorIds",
                "massa", "massas", ids -> flavors.findAllById(ids));
        Map<Long, String> chosenFillings = resolveOptions(request.fillingIds(), size.getMaxFillings(), "fillingIds",
                "recheio", "recheios", ids -> fillings.findAllById(ids));

        // Etapa 2
        if (!availability.isBookable(request.pickupDate(), request.pickupTime())) {
            throw new FieldValidationException("pickupTime",
                    "Este horário não está mais disponível. Escolha outra data ou horário.");
        }

        // Etapa 4
        String topperTheme = blankToNull(request.topperTheme());
        if (request.topperRequested() && topperTheme == null) {
            throw new FieldValidationException("topperTheme", "Informe o tema do topo de bolo.");
        }
        if (!request.topperRequested() && topperPhoto != null && !topperPhoto.isEmpty()) {
            throw new FieldValidationException("topperPhoto", "A foto do topo só pode ser enviada ao solicitar o topo.");
        }

        // Arquivos: tudo validado antes de gravar qualquer coisa
        List<MultipartFile> photos = referencePhotos == null ? List.of()
                : referencePhotos.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (photos.size() > MAX_REFERENCE_PHOTOS) {
            throw new FieldValidationException("referencePhotos",
                    "Envie no máximo " + MAX_REFERENCE_PHOTOS + " fotos de referência.");
        }
        List<ValidatedFile> validPhotos = photos.stream()
                .map(f -> files.validate(f, FilePurpose.ORDER_REFERENCE, "referencePhotos"))
                .toList();
        ValidatedFile validTopperPhoto = topperPhoto == null || topperPhoto.isEmpty() ? null
                : files.validate(topperPhoto, FilePurpose.TOPPER_REFERENCE, "topperPhoto");

        // Gravação
        CakeOrder order = new CakeOrder(newTrackingCode(), name, whatsapp);
        order.schedule(request.pickupDate(), request.pickupTime());
        order.customize(size, blankToNull(request.decoration()), blankToNull(request.cakeText()),
                blankToNull(request.notes()));
        order.chooseFlavors(chosenFlavors);
        order.chooseFillings(chosenFillings);
        if (request.topperRequested()) {
            order.requestTopper(topperTheme, blankToNull(request.topperName()),
                    request.topperAge() == null ? null : request.topperAge().shortValue());
        }
        orders.saveAndFlush(order);

        validPhotos.forEach(f -> files.store(f, order.getId(), FilePurpose.ORDER_REFERENCE, ActorType.CLIENT));
        if (validTopperPhoto != null) {
            files.store(validTopperPhoto, order.getId(), FilePurpose.TOPPER_REFERENCE, ActorType.CLIENT);
        }
        history.record(order.getId(), "CREATED", ActorType.CLIENT, "Solicitação enviada pela cliente.",
                Map.of("photos", validPhotos.size() + (validTopperPhoto == null ? 0 : 1)));

        return new OrderCreatedResponse(TrackingCodes.format(order.getTrackingCode()));
    }

    /** Confere quantidade, duplicidade e se cada item existe e está ativo; devolve id → nome na ordem escolhida. */
    private static Map<Long, String> resolveOptions(List<Long> ids, int max, String field, String singular,
            String plural, Function<List<Long>, List<? extends CatalogOption>> loader) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new FieldValidationException(field, "Não repita a mesma opção.");
        }
        if (ids.size() > max) {
            throw new FieldValidationException(field, "Este tamanho permite escolher até " + max + " "
                    + (max == 1 ? singular : plural) + ".");
        }
        Map<Long, CatalogOption> found = new LinkedHashMap<>();
        loader.apply(ids).forEach(option -> found.put(option.getId(), option));
        Map<Long, String> result = new LinkedHashMap<>();
        for (Long id : ids) {
            CatalogOption option = found.get(id);
            if (option == null || !option.isActive()) {
                throw new FieldValidationException(field, "Uma das opções escolhidas não está mais disponível.");
            }
            result.put(id, option.getName());
        }
        return result;
    }

    private String newTrackingCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = TrackingCodes.generate();
            if (!orders.existsByTrackingCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um código de acompanhamento único");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
