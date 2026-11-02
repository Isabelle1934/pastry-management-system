package br.com.confeitaria.modules.order.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.common.validation.WhatsAppNumber;
import br.com.confeitaria.modules.file.entity.FilePurpose;
import br.com.confeitaria.modules.file.service.FileStorageService;
import br.com.confeitaria.modules.file.service.FileStorageService.ValidatedFile;
import br.com.confeitaria.modules.order.dto.PublicOrderDtos.OrderCreatedResponse;
import br.com.confeitaria.modules.order.dto.PublicOrderDtos.OrderRequest;
import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.order.repository.CakeOrderRepository;

/**
 * Recebe a solicitação de encomenda do formulário público. Toda regra é revalidada aqui,
 * independentemente do que o navegador já validou. A solicitação NÃO ocupa vaga na agenda.
 */
@Service
public class OrderCreationService {

    static final int MAX_REFERENCE_PHOTOS = 3;
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final CakeOrderRepository orders;
    private final OrderDetailsApplier detailsApplier;
    private final FileStorageService files;
    private final OrderHistoryService history;
    private final OrderRateLimiter rateLimiter;

    public OrderCreationService(CakeOrderRepository orders, OrderDetailsApplier detailsApplier,
            FileStorageService files, OrderHistoryService history, OrderRateLimiter rateLimiter) {
        this.orders = orders;
        this.detailsApplier = detailsApplier;
        this.files = files;
        this.history = history;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public OrderCreatedResponse create(OrderRequest request, List<MultipartFile> referencePhotos,
            MultipartFile topperPhoto, String ip) {
        rateLimiter.acquire(ip);

        String name = normalizeName(request.customerName());
        String whatsapp = normalizeWhatsApp(request.customerWhatsapp());
        CakeOrder order = new CakeOrder(newTrackingCode(), name, whatsapp);
        detailsApplier.apply(order, request.details(), OrderDetailsApplier.Actor.CLIENT);

        if (!request.topperRequested() && topperPhoto != null && !topperPhoto.isEmpty()) {
            throw new FieldValidationException("topperPhoto", "A foto do topo só pode ser enviada ao solicitar o topo.");
        }
        // Arquivos: tudo validado antes de gravar qualquer coisa.
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

        orders.saveAndFlush(order);
        validPhotos.forEach(f -> files.store(f, order.getId(), FilePurpose.ORDER_REFERENCE, ActorType.CLIENT));
        if (validTopperPhoto != null) {
            files.store(validTopperPhoto, order.getId(), FilePurpose.TOPPER_REFERENCE, ActorType.CLIENT);
        }
        history.record(order.getId(), "CREATED", ActorType.CLIENT, "Solicitação enviada pela cliente.",
                Map.of("photos", validPhotos.size() + (validTopperPhoto == null ? 0 : 1)));

        return new OrderCreatedResponse(TrackingCodes.format(order.getTrackingCode()));
    }

    static String normalizeName(String name) {
        return name.strip().replaceAll("\\s+", " ");
    }

    static String normalizeWhatsApp(String whatsapp) {
        return WhatsAppNumber.normalize(whatsapp)
                .orElseThrow(() -> new FieldValidationException("customerWhatsapp",
                        "Informe um WhatsApp válido com DDD, ex.: (11) 99999-8888."));
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
}
