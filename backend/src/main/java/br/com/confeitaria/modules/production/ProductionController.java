package br.com.confeitaria.modules.production;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.modules.order.dto.OrderAdminDtos.OrderDetail;
import br.com.confeitaria.modules.production.ProductionDtos.ChangeRequest;
import br.com.confeitaria.modules.production.ProductionDtos.PickupBoard;
import br.com.confeitaria.security.AdminPrincipal;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class ProductionController {

    private final ProductionService production;

    public ProductionController(ProductionService production) {
        this.production = production;
    }

    @GetMapping("/production")
    public PickupBoard board(@RequestParam(defaultValue = "14") int days) {
        return production.board(days);
    }

    @PostMapping("/orders/{id}/production")
    public OrderDetail change(@PathVariable Long id, @Valid @RequestBody ChangeRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return production.change(id, body, admin.getId());
    }
}
