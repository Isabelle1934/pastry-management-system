package br.com.confeitaria.modules.order.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.common.validation.WhatsAppNumber;
import br.com.confeitaria.common.text.Texts;
import br.com.confeitaria.modules.file.entity.FilePurpose;
import br.com.confeitaria.modules.file.service.FileStorageService;
import br.com.confeitaria.modules.file.service.FileStorageService.ValidatedFile;
import br.com.confeitaria.modules.notification.entity.NotificationType;
import br.com.confeitaria.modules.notification.service.NotificationService;
import br.com.confeitaria.modules.order.dto.PublicOrderDtos.OrderCreatedResponse;
import br.com.confeitaria.modules.order.dto.PublicOrderDtos.OrderRequest;
import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.order.repository.CakeOrderRepository;
import br.com.confeitaria.common.exception.ConflictException;

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
    private final NotificationService notifications;
    private final Clock clock;

    /**
     * Janela em que uma solicitação igual (mesma cliente, data, horário e itens) é tratada como envio repetido
     * ({@code app.orders.duplicate-window}, padrão 30 min; zero desliga).
     */
    private Duration duplicateWindow;

    public OrderCreationService(CakeOrderRepository orders, OrderDetailsApplier detailsApplier,
            FileStorageService files, OrderHistoryService history, OrderRateLimiter rateLimiter,
            NotificationService notifications, Clock clock,
            @Value("${app.orders.duplicate-window:PT30M}") Duration duplicateWindow) {
        this.clock = clock;
        this.duplicateWindow = duplicateWindow;
        this.orders = orders;
        this.detailsApplier = detailsApplier;
        this.files = files;
        this.history = history;
        this.rateLimiter = rateLimiter;
        this.notifications = notifications;
    }

    @Transactional
    public OrderCreatedResponse create(OrderRequest request, List<MultipartFile> referencePhotos,
            MultipartFile topperPhoto, String ip) {
        rateLimiter.acquire(ip);

        String name = normalizeName(request.customerName());
        String whatsapp = normalizeWhatsApp(request.customerWhatsapp());
        CakeOrder order = new CakeOrder(newTrackingCode(), name, whatsapp);
        detailsApplier.apply(order, request.details(), OrderDetailsApplier.Actor.CLIENT);
        order.setCustomerEmail(normalizeEmail(request.customerEmail()));
        rejectDuplicate(order);

        if (!request.details().topperRequested() && topperPhoto != null && !topperPhoto.isEmpty()) {
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

        notifications.notify(NotificationType.NEW_ORDER, order.getId(),
                "Nova encomenda de " + name.split(" ")[0] + " para " + Texts.date(order.getPickupDate()) + " às "
                        + order.getPickupTime() + ".",
                "NEW_ORDER:" + order.getId());
        return new OrderCreatedResponse(TrackingCodes.format(order.getTrackingCode()));
    }

    /**
     * Evita solicitações duplicadas (ex.: duplo clique ou reenvio): mesma cliente, mesma data/horário e mesmo
     * conteúdo há pouco tempo. Não informa o código da outra solicitação (só quem a enviou o conhece).
     */
    private void rejectDuplicate(CakeOrder order) {
        if (duplicateWindow.isZero()) {
            return;
        }
        boolean duplicate = orders.findRecentSameSchedule(order.getCustomerWhatsapp(), order.getPickupDate(),
                        order.getPickupTime(), clock.instant().minus(duplicateWindow)).stream()
                .anyMatch(existing -> existing.summary().equals(order.summary()));
        if (duplicate) {
            throw new ConflictException("DUPLICATE_ORDER", "Já recebemos uma solicitação igual a esta há poucos minutos. "
                    + "Use o código que apareceu na confirmação para acompanhar, ou fale com a confeitaria pelo WhatsApp.");
        }
    }

    /** Só para testes: liga/desliga a detecção de envio repetido. */
    public void setDuplicateWindow(Duration duplicateWindow) {
        this.duplicateWindow = duplicateWindow;
    }

    static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.strip().toLowerCase();
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
