package br.com.confeitaria.modules.customer;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.modules.customer.CustomerDtos.CustomerSummary;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.PageResponse;

@RestController
@RequestMapping("/api/admin/customers")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    public PageResponse<CustomerSummary> list(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return customers.list(q, page, size);
    }
}
