package br.com.confeitaria.modules.tracking;

import java.math.BigDecimal;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.confeitaria.modules.file.FileResponses;
import br.com.confeitaria.modules.file.service.FileStorageService;
import br.com.confeitaria.modules.order.dto.PublicOrderDtos.ClientEditRequest;
import br.com.confeitaria.modules.payment.service.PaymentService;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.DeclineRequest;
import br.com.confeitaria.modules.tracking.TrackingDtos.TrackingView;
import br.com.confeitaria.modules.tracking.TrackingDtos.VerifyRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Acompanhamento do pedido pela cliente (sem conta). Todas as rotas exigem a verificação prévia. */
@RestController
@RequestMapping("/api/tracking")
public class TrackingController {

    private final TrackingAccess access;
    private final TrackingService tracking;
    private final PaymentService payments;
    private final FileStorageService storage;

    public TrackingController(TrackingAccess access, TrackingService tracking, PaymentService payments,
            FileStorageService storage) {
        this.access = access;
        this.tracking = tracking;
        this.payments = payments;
        this.storage = storage;
    }

    @PostMapping("/verify")
    public TrackingView verify(@Valid @RequestBody VerifyRequest body, HttpServletRequest request) {
        access.verify(body.trackingCode(), body.whatsappLast4(), request);
        return tracking.view(access.currentOrderId(request));
    }

    @GetMapping("/order")
    public TrackingView order(HttpServletRequest request) {
        return tracking.view(access.currentOrderId(request));
    }

    @PutMapping("/order")
    public TrackingView edit(@Valid @RequestBody ClientEditRequest body, HttpServletRequest request) {
        return tracking.edit(access.currentOrderId(request), body);
    }

    @PostMapping("/order/quotes/{quoteId}/accept")
    public TrackingView accept(@PathVariable Long quoteId, HttpServletRequest request) {
        return tracking.accept(access.currentOrderId(request), quoteId);
    }

    @PostMapping("/order/quotes/{quoteId}/decline")
    public TrackingView decline(@PathVariable Long quoteId, @Valid @RequestBody(required = false) DeclineRequest body,
            HttpServletRequest request) {
        return tracking.decline(access.currentOrderId(request), quoteId, body == null ? null : body.reason());
    }

    /** Comprovante do sinal (JPG, PNG ou PDF). {@code amount} vazio = valor do sinal do orçamento aceito. */
    @PostMapping(path = "/order/payments/deposit-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TrackingView submitProof(@RequestPart("proof") MultipartFile proof,
            @RequestParam(required = false) BigDecimal amount, HttpServletRequest request) {
        Long orderId = access.currentOrderId(request);
        payments.submitProof(orderId, proof, amount);
        return tracking.view(orderId);
    }

    /** Arquivos do próprio pedido (fotos e comprovantes); nunca de outro pedido. */
    @GetMapping("/order/files/{fileId}")
    public ResponseEntity<InputStreamResource> file(@PathVariable Long fileId, HttpServletRequest request) {
        return FileResponses.download(storage, storage.findForOrder(access.currentOrderId(request), fileId));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        access.forget(request);
    }
}
