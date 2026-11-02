package br.com.confeitaria.modules.payment.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.modules.payment.dto.PaymentDtos.ConfirmRequest;
import br.com.confeitaria.modules.payment.dto.PaymentDtos.PaymentSummary;
import br.com.confeitaria.modules.payment.dto.PaymentDtos.RegisterRequest;
import br.com.confeitaria.modules.payment.dto.PaymentDtos.RejectRequest;
import br.com.confeitaria.modules.payment.service.PaymentService;
import br.com.confeitaria.security.AdminPrincipal;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class PaymentAdminController {

    private final PaymentService payments;

    public PaymentAdminController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping("/orders/{orderId}/payments")
    public PaymentSummary summary(@PathVariable Long orderId) {
        return payments.summary(orderId);
    }

    @PostMapping("/orders/{orderId}/payments")
    public PaymentSummary register(@PathVariable Long orderId, @Valid @RequestBody RegisterRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return payments.register(orderId, body, admin.getId());
    }

    @PostMapping("/payments/{paymentId}/confirm")
    public PaymentSummary confirm(@PathVariable Long paymentId, @Valid @RequestBody(required = false) ConfirmRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return payments.confirm(paymentId, body == null ? null : body.note(), body != null && body.acceptsAsDeposit(),
                admin.getId());
    }

    @PostMapping("/payments/{paymentId}/reject")
    public PaymentSummary reject(@PathVariable Long paymentId, @Valid @RequestBody RejectRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return payments.reject(paymentId, body.reason(), admin.getId());
    }
}
