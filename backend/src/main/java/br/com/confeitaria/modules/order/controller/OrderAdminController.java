package br.com.confeitaria.modules.order.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.modules.file.FileResponses;
import br.com.confeitaria.modules.file.service.FileStorageService;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.OrderDetail;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.OrderSummary;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.PageResponse;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.ReasonRequest;
import br.com.confeitaria.modules.order.dto.PublicOrderDtos.OrderRequest;
import br.com.confeitaria.modules.order.entity.CommercialStatus;
import br.com.confeitaria.modules.order.service.OrderAdminService;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.DraftRequest;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.QuoteView;
import br.com.confeitaria.modules.quote.service.QuoteService;
import br.com.confeitaria.security.AdminPrincipal;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/orders")
public class OrderAdminController {

    private final OrderAdminService orders;
    private final QuoteService quotes;
    private final FileStorageService storage;

    public OrderAdminController(OrderAdminService orders, QuoteService quotes, FileStorageService storage) {
        this.orders = orders;
        this.quotes = quotes;
        this.storage = storage;
    }

    @GetMapping
    public PageResponse<OrderSummary> list(@RequestParam(required = false) CommercialStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return orders.list(new OrderAdminService.ListFilter(status, q, from, to), page, size);
    }

    @GetMapping("/counts")
    public Map<CommercialStatus, Long> counts() {
        return orders.countByStatus();
    }

    @GetMapping("/{id}")
    public OrderDetail detail(@PathVariable Long id) {
        return orders.detail(id);
    }

    @PutMapping("/{id}")
    public OrderDetail update(@PathVariable Long id, @Valid @RequestBody OrderRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return orders.update(id, body, admin.getId());
    }

    @PostMapping("/{id}/reject")
    public OrderDetail reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return orders.reject(id, body.reason(), body.version(), admin.getId());
    }

    @PostMapping("/{id}/cancel")
    public OrderDetail cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return orders.cancel(id, body.reason(), body.version(), admin.getId());
    }

    @GetMapping("/{id}/files/{fileId}")
    public ResponseEntity<InputStreamResource> file(@PathVariable Long id, @PathVariable Long fileId) {
        return FileResponses.download(storage, storage.findForOrder(id, fileId));
    }

    // --- Orçamentos ---

    @GetMapping("/{id}/quotes")
    public List<QuoteView> quotes(@PathVariable Long id) {
        return orders.quotes(id);
    }

    @PutMapping("/{id}/quotes/draft")
    public QuoteView saveDraft(@PathVariable Long id, @Valid @RequestBody DraftRequest body) {
        return quotes.saveDraft(id, body);
    }

    @DeleteMapping("/{id}/quotes/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable Long id) {
        quotes.deleteDraft(id);
    }

    @PostMapping("/{id}/quotes/send")
    public QuoteView send(@PathVariable Long id, @AuthenticationPrincipal AdminPrincipal admin) {
        return quotes.send(id, admin.getId());
    }
}
